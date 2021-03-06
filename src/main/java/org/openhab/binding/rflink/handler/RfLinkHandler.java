/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.handler;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.internal.DeviceMessageListener;
import org.openhab.binding.rflink.messages.RfLinkMessage;
import org.openhab.binding.rflink.messages.RfLinkMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RfLinkHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author John Jore - Added initial support to send commands to devices
 * @author Arjan Mels - Added option to repeat messages
 */
public class RfLinkHandler extends BaseThingHandler implements DeviceMessageListener {

    public static final int TIME_BETWEEN_COMMANDS = 50;
    private Logger logger = LoggerFactory.getLogger(RfLinkHandler.class);

    private RfLinkBridgeHandler bridgeHandler;

    private RfLinkDeviceConfiguration config;

    public RfLinkHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received channel: {}, command: {}", channelUID, command);

        if (bridgeHandler != null) {
            if (command instanceof RefreshType) {
                // Not supported
            } else {
                try {
                    RfLinkMessage message = RfLinkMessageFactory
                            .createMessageForSendingToThing(getThing().getThingTypeUID());
                    message.initializeFromChannel(getConfigAs(RfLinkDeviceConfiguration.class), channelUID, command);
                    updateThingStates(message);
                    int repeats = 1;
                    if (getThing().getConfiguration().containsKey("repeats")) {
                        repeats = ((BigDecimal) getThing().getConfiguration().get("repeats")).intValue();
                    }
                    repeats = Math.min(Math.max(repeats, 1), 20);
                    for (int i = 0; i < repeats; i++) {
                        waitBeforeCommandExecution(i);
                        bridgeHandler.sendMessage(message);
                    }
                } catch (RfLinkNotImpException e) {
                    logger.error("Message not supported: {}", e.getMessage());
                } catch (RfLinkException e) {
                    logger.error("Transmitting error: {}", e.getMessage());
                }
            }
        }
    }

    private void waitBeforeCommandExecution(int i) {
        if (i > 0) {
            try {
                Thread.sleep(TIME_BETWEEN_COMMANDS);
            } catch (InterruptedException e) {
                logger.error("Sleep time between command repeat ended in error", e);
            }
        }
    }

    /**
     */
    @Override
    public void initialize() {
        config = getConfigAs(RfLinkDeviceConfiguration.class);
        logger.debug("Initializing thing {}, deviceId={}", getThing().getUID(), config.deviceId);
        initializeBridge((getBridge() == null) ? null : getBridge().getHandler(),
                (getBridge() == null) ? null : getBridge().getStatus());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {} for thing {}", bridgeStatusInfo, getThing().getUID());
        initializeBridge((getBridge() == null) ? null : getBridge().getHandler(), bridgeStatusInfo.getStatus());
    }

    private void initializeBridge(ThingHandler thingHandler, ThingStatus bridgeStatus) {
        logger.debug("initializeBridge {} for thing {}", bridgeStatus, getThing().getUID());

        config = getConfigAs(RfLinkDeviceConfiguration.class);
        if (config.deviceId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "RFLink device missing deviceId");
        } else if (thingHandler != null && bridgeStatus != null) {
            bridgeHandler = (RfLinkBridgeHandler) thingHandler;
            bridgeHandler.registerDeviceStatusListener(this);

            if (bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }

        // super.bridgeHandlerInitialized(thingHandler, bridge);
    }

    @Override
    public void dispose() {
        logger.debug("Thing {} disposed.", getThing().getUID());
        if (bridgeHandler != null) {
            bridgeHandler.unregisterDeviceStatusListener(this);
        }
        bridgeHandler = null;
        super.dispose();
    }

    @Override
    public void onDeviceMessageReceived(ThingUID bridge, RfLinkMessage message) {

        try {
            String id = message.getDeviceId();
            // logger.debug("Matching Message from bridge {} from device [{}] with [{}]", bridge.toString(), id,
            // config.deviceId);
            if (config.deviceId.equals(id)) {
                logger.debug("Message from bridge {} from device [{}] type [{}] matched", bridge.toString(), id,
                        message.getClass().getSimpleName());
                updateStatus(ThingStatus.ONLINE);
                updateThingStates(message);

            }

        } catch (RfLinkException e) {
            logger.error("Error occured during message receiving", e);
        }
    }

    private void updateThingStates(RfLinkMessage message) {
        Map<String, State> map = message.getStates();
        for (String channel : map.keySet()) {
            logger.debug("Update channel: {}, state: {}", channel, map.get(channel));
            updateState(new ChannelUID(getThing().getUID(), channel), map.get(channel));
        }
    }
}
