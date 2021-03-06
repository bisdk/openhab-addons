/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bisecuregateway.internal.discovery;

import static org.openhab.binding.bisecuregateway.internal.BiSecureGatewayBindingConstants.*;

import java.util.List;

import org.bisdk.sdk.Group;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.bisecuregateway.internal.BiSecureGatewayHandlerFactory;
import org.openhab.binding.bisecuregateway.internal.handler.BiSecureGatewayHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BiSecureDeviceDiscoveryService} class discovers BiSecure Devices connected to a BiSecure Gateway
 *
 * @author Thomas Letsch - Initial contribution
 */
@NonNullByDefault
public class BiSecureDeviceDiscoveryService extends AbstractDiscoveryService {

    private static final int TIMEOUT = 5;

    private final Logger logger = LoggerFactory.getLogger(BiSecureDeviceDiscoveryService.class);
    private final BiSecureGatewayHandler bridgeHandler;

    public BiSecureDeviceDiscoveryService(BiSecureGatewayHandler bridgeHandler) {
        super(BiSecureGatewayHandlerFactory.SUPPORTED_THING_TYPES_UIDS, TIMEOUT, true);
        logger.debug("BiSecureDeviceDiscoveryService {}", bridgeHandler);
        this.bridgeHandler = bridgeHandler;
    }

    @Override
    protected void startScan() {
        discoverDevices();
    }

    @Override
    protected void startBackgroundDiscovery() {
        discoverDevices();
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }

    /**
     * Discovers devices connected to a hub
     */
    private void discoverDevices() {
        if (bridgeHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("BiSecure Gateway not online, scanning postponed");
            return;
        }
        if (bridgeHandler.getClientAPI() == null) {
            logger.debug("ClientAPI not yet ready, scanning postponed");
            return;
        }
        logger.debug("getting devices on {}", bridgeHandler.getThing().getUID().getId());
        try {
            List<Group> groups = bridgeHandler.getGroups();
            groups.forEach(group -> {
                addDiscoveryResults(group);
            });
        } catch (Exception e) {
            logger.warn("Could not discover BiSecure groups, cause: " + e);
        }
    }

    private void addDiscoveryResults(Group group) {
        String name = group.getName();
        int id = group.getId();
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID thingUID = new ThingUID(GROUP_THING_TYPE, bridgeUID, id + "");
        // @formatter:off
        thingDiscovered(DiscoveryResultBuilder.create(thingUID)
                .withLabel(name)
                .withBridge(bridgeUID)
                .withProperty(PROPERTY_ID, id + "")
                .withProperty(PROPERTY_NAME, name)
                .build());
         // @formatter:on
    }
}
