/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.pentair.internal.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.config.PentairSerialBridgeConfig;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the IPBridge. Implements the connect and disconnect abstract methods of {@link PentairBaseBridgeHandler}
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairSerialBridgeHandler extends PentairBaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairSerialBridgeHandler.class);

    public PentairSerialBridgeConfig config = new PentairSerialBridgeConfig();
    /** SerialPort object representing the port where the RS485 adapter is connected */
    private final SerialPortManager serialPortManager;
    private Optional<SerialPort> port = Optional.empty();
    private @Nullable SerialPortIdentifier portIdentifier;

    public PentairSerialBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
    }

    @Override
    protected synchronized boolean connect() {
        config = getConfigAs(PentairSerialBridgeConfig.class);

        if (config.serialPort.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "no serial port configured");
            return false;
        }

        this.id = config.id;
        logger.debug("Serial port id: {}", id);
        this.discovery = config.discovery;

        portIdentifier = serialPortManager.getIdentifier(config.serialPort);
        if (portIdentifier == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Configured serial port does not exist");
            return false;
        }

        try {
            logger.debug("connect port: {}", config.serialPort);

            Objects.requireNonNull(portIdentifier, "portIdentifier is null");
            if (portIdentifier.isCurrentlyOwned()) {
                logger.debug("Serial port is currently being used by another application {}",
                        portIdentifier.getCurrentOwner());
                // for debug purposes, will continue to try and open
            }

            port = Optional.of(portIdentifier.open("org.openhab.binding.pentair", 10000));

            if (!port.isPresent()) {
                return false;
            }

            port.get().setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            port.get().setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

            InputStream is = port.get().getInputStream();
            OutputStream os = port.get().getOutputStream();

            if (is != null) {
                setInputStream(is);
            }

            if (os != null) {
                setOutputStream(os);
            }
        } catch (PortInUseException e) {
            String msg = String.format("Serial port already in use: %s, %s", config.serialPort, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return false;
        } catch (UnsupportedCommOperationException e) {
            String msg = String.format("got unsupported operation %s on port %s, %s", e.getMessage(), config.serialPort,
                    e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return false;
        } catch (IOException e) {
            String msg = String.format("got IOException %s on port %s", e.getMessage(), config.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return false;
        }

        // if you have gotten this far, you should be connected to the serial port
        logger.debug("Pentair Bridge connected to serial port: {}", config.serialPort);

        updateStatus(ThingStatus.ONLINE);

        return true;
    }

    @Override
    protected synchronized void disconnect() {
        updateStatus(ThingStatus.OFFLINE);

        if (port.isPresent()) {
            port.get().close();
            port = Optional.empty();
        }
    }
}
