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
package org.openhab.binding.bisecuregateway.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link BiSecureGatewayBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Thomas Letsch - Initial contribution
 */
@NonNullByDefault
public class BiSecureGatewayBindingConstants {

    public static final String BINDING_ID = "bisecuregateway";

    // List of all Thing Type UIDs
    public static final ThingTypeUID GATEWAY_BRIDGE_TYPE = new ThingTypeUID(BINDING_ID, "bisecuregateway");
    public static final ThingTypeUID GROUP_THING_TYPE = new ThingTypeUID(BINDING_ID, "bisecuregroup");

    // List of all properties
    public static final String PROPERTY_MAC = "mac";
    public static final String PROPERTY_GATEWAY_ID = "gatewayId";
    public static final String PROPERTY_GATEWAY_ADDRESS = "gatewayAddress";
    public static final String PROPERTY_SOFTWARE_VERSION = "swVersion";
    public static final String PROPERTY_HARDWARE_VERSION = "hwVersion";
    public static final String PROPERTY_PROTOCOL = "protocol";
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_ID = "id";
    public static final String PROPERTY_TYPE = "type";

    // List of all Channel ids
    public static final String CHANNEL_TYPE_ERROR = "ERROR";

    public static final String ITEM_TYPE_ROLLERSHUTTER = "Rollershutter";
    public static final String ITEM_TYPE_ERROR = "String";
}
