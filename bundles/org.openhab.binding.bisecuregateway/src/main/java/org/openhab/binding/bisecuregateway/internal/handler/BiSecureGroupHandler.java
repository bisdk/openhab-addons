/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bisecuregateway.internal.handler;

import static org.openhab.binding.bisecuregateway.internal.BiSecureGatewayBindingConstants.*;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bisdk.PermissionDeniedException;
import org.bisdk.sdk.*;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bisecuregateway.internal.BiSecureGatewayBindingConstants;
import org.openhab.binding.bisecuregateway.internal.BiSecureGatewayConfiguration;
import org.openhab.binding.bisecuregateway.internal.BiSecureGatewayHandlerFactory;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BiSecureGroupHandler} is responsible for handling commands for BiSecure Groups (Devices), which are
 * sent to one of the channels.
 *
 * @author Thomas Letsch - Initial contribution
 */
@NonNullByDefault
public class BiSecureGroupHandler extends BaseThingHandler {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(GROUP_THING_TYPE);
    /**
     * After 4 times a get state fails, we set the thing offline
     */
    private static final int MAX_ERRORS_IN_A_ROW = 4;
    private final Logger logger = LoggerFactory.getLogger(BiSecureGroupHandler.class);
    private Map<ChannelUID, Port> ports = new HashMap<ChannelUID, Port>();

    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable ScheduledFuture<?> initializingJob;

    private List<Channel> channels = Collections.emptyList();

    /**
     * Map of the group channel id to the corresponding error channel
     */
    private Map<ChannelUID, Channel> errorChannels = new HashMap<>();

    private Map<ChannelUID, State> oldChannelStates = new HashMap<>();

    private int consecutiveErrors = 0;
    private boolean activePolling = false;
    private long activePollingStartedAt = 0;

    private @Nullable BiSecureGatewayConfiguration bindingConfig;

    public BiSecureGroupHandler(Thing thing, BiSecureGatewayHandlerFactory factory) {
        super(thing);
    }

    protected @Nullable BiSecureGatewayHandler getBiSecureGatewayHandler() {
        Bridge bridge = getBridge();
        return bridge != null ? (BiSecureGatewayHandler) bridge.getHandler() : null;
    }

    @Override
    public void initialize() {
        BiSecureGatewayHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.debug("BiSecureGatewayHandler not yet ready, cannot initialize");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "BiSecureGatewayHandler not yet ready, cannot initialize");
            return;
        }
        bindingConfig = bridgeHandler.getBindingConfig();
        createInitializingThread();
        createPollingThread(bindingConfig.getPollingInterval());
    }

    private void createInitializingThread() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("Running initializing thread");
                try {
                    BiSecureGatewayHandler bridgeHandler = getBridgeHandler();
                    if (bridgeHandler != null && getThing().getStatus().equals(ThingStatus.INITIALIZING)) {
                        initializeGroups(bridgeHandler);
                    }
                    if (getThing().getStatus().equals(ThingStatus.ONLINE)) {
                        initializingJob.cancel(false);
                    }
                } catch (Exception e) {
                    logger.error("Got error while trying to get groups. Will retry in 30sec... ", e);
                }
            }
        };
        if (initializingJob != null) {
            initializingJob.cancel(true);
        }
        initializingJob = scheduler.scheduleAtFixedRate(runnable, 0, 30, TimeUnit.SECONDS);
    }

    private void initializeGroups(BiSecureGatewayHandler bridgeHandler) {
        List<Group> groups = bridgeHandler.getGroups();
        String groupId = getThing().getProperties().get(PROPERTY_ID);
        int groupIdInt = groupId != null ? Integer.parseInt(groupId) : 0;
        Group group = null;
        for (Group toBeChecked : groups) {
            if (toBeChecked.getId() == groupIdInt) {
                group = toBeChecked;
            }
        }
        if (group == null) {
            logger.debug("Group " + groupId + " not found in gateway!");
            return;
        }
        channels = new ArrayList<Channel>();
        group.getPorts().forEach(port -> {
            PortType portType = PortType.Companion.from(port.getType());
            String portTypeName = (portType != null) ? portType.name() : ""; // e.g. "IMPULS" for garage door control
            ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, portTypeName);
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), port.getId() + "_" + portTypeName);
            Channel channel = ChannelBuilder.create(channelUID, BiSecureGatewayBindingConstants.ITEM_TYPE_ROLLERSHUTTER)
                    .withType(channelTypeUID).build();
            channels.add(channel);
            ChannelTypeUID errorChannelTypeUID = new ChannelTypeUID(BINDING_ID, CHANNEL_TYPE_ERROR);
            ChannelUID errorChannelUID = new ChannelUID(getThing().getUID(), port.getId() + "_ERROR");
            Channel errorChannel = ChannelBuilder
                    .create(errorChannelUID, BiSecureGatewayBindingConstants.ITEM_TYPE_ERROR)
                    .withType(errorChannelTypeUID).build();
            errorChannels.put(channelUID, errorChannel);
            ports.put(channelUID, port);
        });

        ThingBuilder thingBuilder = editThing();
        List<Channel> allChannels = new ArrayList<>(channels);
        allChannels.addAll(errorChannels.values());
        thingBuilder.withChannels(allChannels);
        updateThing(thingBuilder.build());
        updateStatus(ThingStatus.ONLINE);
    }

    private void createPollingThread(int seconds) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (Channel channel : channels) {
                    updateChannelState(channel.getUID());
                }
            }
        };
        if (pollingJob != null) {
            pollingJob.cancel(true);
        }
        pollingJob = scheduler.scheduleAtFixedRate(runnable, 0, seconds, TimeUnit.SECONDS);
    }

    private void updateChannelState(ChannelUID channelUID) {
        ClientAPI clientAPI = getClientAPI();
        if (clientAPI == null) {
            return;
        }
        try {
            Transition transition = clientAPI.getTransition(ports.get(channelUID));
            PercentType newState = new PercentType(100 - transition.getStateInPercent());
            if (oldChannelStates.get(channelUID) != null && oldChannelStates.get(channelUID).equals(newState)) {
                logger.debug("Channel state of " + channelUID + " did not change.");
            } else {
                logger.debug("Set channel state of " + channelUID + " to " + newState);
                updateState(channelUID, newState);
            }
            updateErrorChannel(channelUID, "");
            if (getThing().getStatus() == ThingStatus.OFFLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
            consecutiveErrors = 0;
            checkActivePolling(transition);
        } catch (PermissionDeniedException e) {
            clientAPI.relogin();
            updateErrorChannel(channelUID, "Permission denied - will retry");
        } catch (IllegalStateException e) {
            updateErrorChannel(channelUID, "Illegal state: " + e.getMessage());
            consecutiveErrors++;
            if (consecutiveErrors > MAX_ERRORS_IN_A_ROW) {
                // Retry and reconnect failed => set thing to status offline
                updateStatus(ThingStatus.OFFLINE);
            }
        } catch (Exception e) {
            updateErrorChannel(channelUID, "Unknown problem: " + e.getMessage());
            // We ignore errors here
        }
    }

    private void checkActivePolling(Transition transition) {
        long timeSinceActivePollingStarted = getCurrentTimeSeconds() - activePollingStartedAt;
        boolean activePollingTimedOut = timeSinceActivePollingStarted >= bindingConfig.getActivePollingTimeout();
        boolean isMoving = transition.isDriving();
        boolean isOpen = transition.getStateInPercent() == 0;
        boolean isOpenAndShouldStayActive = isOpen && bindingConfig.getActivePollingDuringOpened();
        if (activePolling && activePollingTimedOut && !isMoving && !isOpenAndShouldStayActive) {
            startActivePolling();
        } else if (!activePolling && isOpenAndShouldStayActive) {
            stopActivePolling();
        }
    }

    private void updateErrorChannel(ChannelUID channelUID, String message) {
        Channel errorChannel = errorChannels.get(channelUID);
        if (errorChannel == null) {
            return;
        }
        StringType newState = new StringType(message);
        if (oldChannelStates.get(channelUID) != null && oldChannelStates.get(channelUID).equals(newState)) {
            logger.debug("Channel state of " + channelUID + " did not change.");
        } else {
            updateState(errorChannel.getUID(), newState);
        }
    }

    private @Nullable ClientAPI getClientAPI() {
        BiSecureGatewayHandler biSecureGatewayHandler = getBridgeHandler();
        if (biSecureGatewayHandler == null) {
            logger.warn("Bridge handler is null, cannot get clientAPI");
            return null;
        }
        ClientAPI clientAPI = biSecureGatewayHandler.getClientAPI();
        return clientAPI;
    }

    private @Nullable BiSecureGatewayHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.warn("Bridge is null, cannot get clientAPI");
            return null;
        }
        BridgeHandler bridgeHandler = bridge.getHandler();
        if (bridgeHandler == null) {
            logger.warn("Bridge handler is null, cannot get clientAPI");
            return null;
        }
        BiSecureGatewayHandler biSecureGatewayHandler = (BiSecureGatewayHandler) bridgeHandler;
        return biSecureGatewayHandler;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("Handling command '{}' for {}", command, channelUID);
        ClientAPI clientAPI = getClientAPI();
        if (clientAPI == null) {
            logger.warn("ClientAPI not yet ready, cannot handle command");
            return;
        }
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("Gateway is offline, ignoring command {} for channel {}", command, channelUID);
            return;
        }

        if (command instanceof RefreshType) {
            updateChannelState(channelUID);
            return;
        }

        Transition transition = clientAPI.getTransition(ports.get(channelUID));
        if (shouldTriggerImpulse(command, transition)) {
            clientAPI.setState(ports.get(channelUID));
            stopActivePolling();
        } else {
            logger.debug("Command " + command + " ignored since current state is already correct.");
        }
        return;
    }

    private void startActivePolling() {
        createPollingThread(bindingConfig.getPollingInterval());
        activePolling = false;
    }

    private void stopActivePolling() {
        createPollingThread(bindingConfig.getActivePollingInterval());
        activePolling = true;
        activePollingStartedAt = getCurrentTimeSeconds();
    }

    private long getCurrentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private boolean shouldTriggerImpulse(Command command, Transition transition) {
        PercentType newState = new PercentType(100 - transition.getStateInPercent());
        if (command instanceof UpDownType) {
            UpDownType finalType = (UpDownType) command;

            if (transition.isDriving()) {
                // Sending an impuls will stop, we only want to stop if driving in wrong direction
                if (transition.getHcp().getDrivingToClose()) {
                    return finalType == UpDownType.UP;
                } else {
                    return finalType == UpDownType.DOWN;
                }
            }

            if (finalType == UpDownType.DOWN) {
                if (newState.intValue() != 100) {
                    // state is OPEN and command is close
                    return true;
                }
            }
            if (finalType == UpDownType.UP) {
                if (newState.intValue() != 0) {
                    // state is CLOSE and command is open
                    return true;
                }
            }
        }
        if (command instanceof StopMoveType) {
            StopMoveType finalType = (StopMoveType) command;
            if (finalType == StopMoveType.MOVE && !transition.isDriving()) {
                // state is not DRIVING and command is MOVE
                return true;
            }
            if (finalType == StopMoveType.STOP && transition.isDriving()) {
                // state is DRIVING and command is STOP
                return true;
            }
        }
        // We should not trigger an impulse since desired state is already actual state
        return false;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge Status changed: " + bridgeStatusInfo.getStatus());
    }

    @Override
    public void dispose() {
        if (pollingJob != null) {
            pollingJob.cancel(true);
        }
        if (initializingJob != null) {
            initializingJob.cancel(true);
        }
    }
}
