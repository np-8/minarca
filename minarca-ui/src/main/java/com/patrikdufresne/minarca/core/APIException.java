/*
 * Copyright (C) 2015, Patrik Dufresne Service Logiciel inc. All rights reserved.
 * Patrik Dufresne Service Logiciel PROPRIETARY/CONFIDENTIAL.
 * Use is subject to license terms.
 */
package com.patrikdufresne.minarca.core;

import static com.patrikdufresne.minarca.Localized._;

public class APIException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Raised when trying to link a computer with a name already in use in minarca.
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class ComputerNameAlreadyInUseException extends APIException {
        public ComputerNameAlreadyInUseException(String computername) {
            super(_("computer name {0} already in use", computername));
        }
    }

    /**
     * Thrown when the application is not configured.
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class NotConfiguredException extends APIException {
        public NotConfiguredException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the application is miss configured
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class MissConfiguredException extends APIException {
        public MissConfiguredException(String message) {
            super(message);
        }

        public MissConfiguredException(String message, Exception cause) {
            super(message, cause);
        }
    }

    /**
     * Raised when plink.exe is missing.
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class PlinkMissingException extends APIException {

        public PlinkMissingException() {
            super(_("plink is missing"));
        }

    }

    public static class UntrustedHostKey extends APIException {

        // TODO Add mos arguments: fingerprint, hostname
        public UntrustedHostKey() {
            super(_("remote SSH host is not trusted"));
        }

    }

    /**
     * Raised when the running OS is not supported.
     */
    public static class UnsupportedOS extends APIException {

        public UnsupportedOS() {
            super(_("Minarca doesn't support your operating system. This application will close."));
        }

    }

    public static class UnsufficientPermissons extends APIException {

        public UnsufficientPermissons() {
            super(_("you don't have sufficient permissions to execute this application!"));
        }

    }

    /**
     * Raised when the task is not found.
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class ScheduleNotFoundException extends APIException {

        public ScheduleNotFoundException() {
            super(_("scheduled task not found"));
        }

    }

    /**
     * Raised when link with minarca failed.
     * 
     * @author Patrik Dufresne
     * 
     */
    public static class LinkComputerException extends APIException {
        public LinkComputerException() {
            this(null, null);
        }

        public LinkComputerException(String message) {
            this(message, null);
        }

        public LinkComputerException(Exception cause) {
            this(null, cause);
        }

        public LinkComputerException(String message, Exception cause) {
            super(message != null ? _("fail to link computer: {0}", message) : _("fail to link computer"), cause);
        }

    }

    public APIException(String message) {
        super(message);
    }

    public APIException(Exception cause) {
        super(cause);
    }

    public APIException(String message, Exception e) {
        super(message, e);
    }

}
