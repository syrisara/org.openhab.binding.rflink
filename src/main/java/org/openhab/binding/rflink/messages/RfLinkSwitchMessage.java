/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.messages;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RfLink data class for power switch message.
 *
 * @author Daan Sieben - Initial contribution
 * @author John Jore - Added channel for Contacts
 * @author Arjan Mels - Simplified by using system OnOffType and OpenClosedType
 * @author John Jore - Simplification breaks "Contacts" as RfLink outputs OFF/ON, not OPEN/CLOSED. Reverted
 */
public class RfLinkSwitchMessage extends RfLinkBaseMessage {
    private static final String KEY_SWITCH = "SWITCH";
    private static final String KEY_CMD = "CMD";

    private static final Collection<String> KEYS = Arrays.asList(KEY_SWITCH, KEY_CMD);
    private static Logger logger = LoggerFactory.getLogger(RfLinkSwitchMessage.class);

    public Type command = OnOffType.OFF;
    public Type contact = OpenClosedType.CLOSED;

    public RfLinkSwitchMessage() {
    }

    public RfLinkSwitchMessage(String data) {
        encodeMessage(data);
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_SWITCH;
    }

    @Override
    public String toString() {
        String str = super.toString();
        str += ", Command = " + command;
        str += ", Contact = " + contact;
        return str;
    }

    @Override
    public void encodeMessage(String data) {
        super.encodeMessage(data);

        if (values.containsKey(KEY_CMD)) {
            try {
                command = RfLinkTypeUtils.getTypeFromStringValue(values.get(KEY_CMD));
            } catch (Exception e) {
                logger.error("Can't convert " + values.get(KEY_CMD) + " to Switch Command", e);
            }
            try {
                contact = RfLinkTypeUtils.getSynonym(command, OpenClosedType.class);
                if (contact == null) {
                    logger.error("Can't convert " + values.get(KEY_CMD) + " to Contact state");
                }
            } catch (Exception e) {
                contact = null;
            }

        }

        if (values.containsKey(KEY_SWITCH)) {
            this.deviceId += ID_DELIMITER + values.get(KEY_SWITCH);
        }
    }

    @Override
    public Collection<String> keys() {
        return KEYS;
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_COMMAND, (State) command);
        map.put(RfLinkBindingConstants.CHANNEL_CONTACT, (State) contact);
        return map;
    }

    @Override
    public void initializeFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command triggeredCommand)
            throws RfLinkNotImpException, RfLinkException {
        super.initializeFromChannel(config, channelUID, triggeredCommand);
        command = OnOffType.valueOf(triggeredCommand.toFullString());
    }

    @Override
    public String decodeMessageAsString(String suffix) {
        return super.decodeMessageAsString(this.command.toFullString());
    }
}
