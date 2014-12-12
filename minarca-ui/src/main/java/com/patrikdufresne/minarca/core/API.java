/*
 * Copyright (c) 2014, Patrik Dufresne Service Logiciel. All rights reserved.
 * Patrik Dufresne Service Logiciel PROPRIETARY/CONFIDENTIAL.
 * Use is subject to license terms.
 */
package com.patrikdufresne.minarca.core;

import static com.patrikdufresne.minarca.Localized._;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.patrikdufresne.minarca.core.APIException.MissConfiguredException;
import com.patrikdufresne.minarca.core.APIException.NotConfiguredException;
import com.patrikdufresne.minarca.core.internal.Keygen;
import com.patrikdufresne.minarca.core.internal.SSH;
import com.patrikdufresne.minarca.core.internal.Scheduler;

/**
 * This class is the main entry point to do everything related to minarca.
 * 
 * @author Patrik Dufresne
 * 
 */
public enum API {
    INSTANCE;
    /**
     * Application name used for sub folder
     */
    private static final String APPNAME = "minarca";

    /**
     * Base URL. TODO change this.
     */
    protected static final String BASE_URL = "http://rdiffweb.patrikdufresne.com";

    /**
     * Property name.
     */
    private static final String COMPUTERNAME = "computername";

    /**
     * Filename used for configuration file. Notice, this is also read by batch file.
     */
    private static final String CONF_FILENAME = "conf";

    /**
     * The remote host.
     */
    private static final String DEFAULT_REMOTEHOST = "fente.patrikdufresne.com";

    /**
     * List of finger print accepted by minarca. For security reason, we only accept known finger print.
     */
    public static final String[] DEFAULT_REMOTEHOST_FINGERPRINT = new String[] { "05:16:1c:49:37:58:87:a5:5c:16:31:bc:a9:95:2c:c2" };

    /**
     * Exclude filename.
     */
    private static final String EXCLUDES_FILENAME = "excludes";

    /**
     * Includes filename.
     */
    private static final String INCLUDES_FILENAME = "includes";

    /**
     * The logger.
     */
    private static final transient Logger LOGGER = LoggerFactory.getLogger(API.class);

    /**
     * Property name.
     */
    private static final String REMOTEHOST = "remotehost";

    /**
     * Property name.
     */
    private static final String USERNAME = "username";

    /**
     * Return the location of the configuration.
     * 
     * @return return a File instance.
     */
    public static File getConfigDirFile() {
        if (SystemUtils.IS_OS_WINDOWS) { //$NON-NLS-1$
            File configDir;
            if (SystemUtils.IS_OS_WINDOWS_XP || SystemUtils.IS_OS_WINDOWS_2003) {
                configDir = new File(System.getenv("ALLUSERSPROFILE") + "/Application Data/minarca");
            } else {
                configDir = new File(System.getenv("PROGRAMDATA") + "/minarca");
            }
            //$NON-NLS-1$
            configDir.mkdirs();
            return configDir;
        } else if (SystemUtils.IS_OS_LINUX) {
            File configDir = new File(SystemUtils.getUserHome(), ".config/" + APPNAME); //$NON-NLS-1$
            configDir.mkdirs();
            return configDir;
        }
        throw unsupportedOS();
    }

    /**
     * Return a default computer name to represent this computer.
     * <p>
     * Current implementation gets the hostname.
     * 
     * @return an empty string or a hostname
     */
    public static String getDefaultComputerName() {
        // For windows
        String host = System.getenv("COMPUTERNAME");
        if (host != null) return host.toLowerCase();
        // For linux
        host = System.getenv("HOSTNAME");
        if (host != null) return host.toLowerCase();
        // Fallback and use Inet interface.
        try {
            String result = InetAddress.getLocalHost().getHostName();
            if (StringUtils.isNotEmpty(result)) return result.toLowerCase();
        } catch (UnknownHostException e) {
            // failed; try alternate means.
        }
        return "";
    }

    /**
     * Represent the operating system file to be ignored.
     */
    public static List<GlobPattern> getSysFilesExcludes() {
        if (SystemUtils.IS_OS_WINDOWS) {
            String userHome = System.getProperty("user.home");
            List<GlobPattern> list = new ArrayList<GlobPattern>();
            list.add(new GlobPattern("**/pagefile.sys"));
            list.add(new GlobPattern("**/NTUSER.DAT*"));
            list.add(new GlobPattern("**/desktop.ini"));
            list.add(new GlobPattern("**/ntuser.ini"));
            list.add(new GlobPattern("**/Thumbs.db"));
            list.add(new GlobPattern("**/Default.rdp"));
            list.add(new GlobPattern("**/ntuser.dat*"));
            list.add(new GlobPattern("C:/Recovery/"));
            list.add(new GlobPattern("C:/ProgramData/"));
            list.add(new GlobPattern("C:/$Recycle.Bin/"));
            String windowDir = System.getenv("SystemRoot");
            if (windowDir != null) {
                list.add(new GlobPattern(windowDir));
            }
            String temp = System.getenv("TEMP");
            if (temp != null) {
                list.add(new GlobPattern(temp));
            }
            list.add(new GlobPattern(userHome + "/AppData/"));
            list.add(new GlobPattern(userHome + "/Tracing/"));
            list.add(new GlobPattern(userHome + "/Recent/"));
            list.add(new GlobPattern(userHome + "/PrintHood/"));
            list.add(new GlobPattern(userHome + "/NetHood/"));
            list.add(new GlobPattern(userHome + "/Searches/"));
            return list;
        } else if (SystemUtils.IS_OS_LINUX) {
            return Arrays.asList(new GlobPattern(".*"), new GlobPattern("*~"));
        }
        throw unsupportedOS();
    }

    /**
     * Represent the default location where files are downloaded.
     * 
     * @return
     */
    public static List<GlobPattern> getDownloadsExcludes() {
        if (SystemUtils.IS_OS_WINDOWS) {
            String userHome = System.getProperty("user.home");
            List<GlobPattern> list = new ArrayList<GlobPattern>();
            list.add(new GlobPattern(userHome + "/Downloads/"));
            return list;
        } else if (SystemUtils.IS_OS_LINUX) {
            String userHome = System.getProperty("user.home");
            List<GlobPattern> list = new ArrayList<GlobPattern>();
            list.add(new GlobPattern(userHome + "/Downloads/"));
            return list;
        }
        throw unsupportedOS();
    }

    public static List<GlobPattern> getDefaultIncludes() {
        if (SystemUtils.IS_OS_WINDOWS) {
            // Return user directory.
            return Arrays.asList(new GlobPattern(SystemUtils.getUserHome()));
        } else if (SystemUtils.IS_OS_LINUX) {
            // Return user directory.
            return Arrays.asList(new GlobPattern(SystemUtils.getUserHome()));
        }
        throw unsupportedOS();
    }

    /**
     * Return the home driver ( the only drive to be backup.
     * 
     * @return
     */
    public static File getHomeDrive() {
        String homedrive = System.getenv("HOMEDRIVER");
        // Check if env variable contains something takt make senses
        File file = new File(homedrive + SystemUtils.PATH_SEPARATOR);
        if (file.exists() && file.isDirectory()) {
            return file;
        }
        return new File("C:\\");
    }

    private static UnsupportedOperationException unsupportedOS() {
        return new UnsupportedOperationException(SystemUtils.OS_NAME + " not supported");
    }

    /**
     * Reference to the configuration file.
     */
    private File confFile;

    /**
     * Reference to exclude file list.
     */
    private File excludesFile;

    /**
     * Reference to include file list.
     */
    private File includesFile;

    /**
     * Reference to the configuration.
     */
    private Properties properties;

    /**
     * Default constructor.
     */
    private API() {

        File configDir = getConfigDirFile();
        this.confFile = new File(configDir, CONF_FILENAME); //$NON-NLS-1$
        this.includesFile = new File(configDir, INCLUDES_FILENAME); //$NON-NLS-1$
        this.excludesFile = new File(configDir, EXCLUDES_FILENAME); //$NON-NLS-1$

        // Load the configuration
        this.properties = new Properties();
        try {
            FileInputStream in = new FileInputStream(confFile);
            this.properties.load(in);
            in.close();
        } catch (IOException e) {
            LoggerFactory.getLogger(API.class).warn(_("can't load properties {}"), confFile);
        }
    }

    /**
     * Used to check if the configuration is OK. Called as a sanity check to make sure "minarca" is properly configured.
     * If not, it throw an exception.
     * 
     * @return
     */
    public void checkConfig() throws APIException {
        // Basic sanity check to make sure it's configured. If not, display the
        // setup dialog.
        if (StringUtils.isEmpty(getComputerName()) || StringUtils.isEmpty(getRemotehost()) || StringUtils.isEmpty(getUsername())) {
            throw new NotConfiguredException(_("minarca is not configured"));
        }
        if (getIncludes().isEmpty() || getExcludes().isEmpty()) {
            throw new MissConfiguredException(_("includes or excludes pattern are missing"));
        }
        if (!Scheduler.getInstance().exists()) {
            throw new MissConfiguredException(_("scheduled tasks is missing"));
        }
    }

    /**
     * Establish connection to minarca.
     * 
     * @return a new client
     * @throws APIException
     */
    public Client connect(String username, String password) throws APIException {
        return new Client(username, password);
    }

    /**
     * This method is called to sets the default configuration for includes, excludes and scheduled task.
     * 
     * @throws APIException
     */
    public void defaultConfig() throws APIException {
        LOGGER.debug("restore default config");
        // Sets the default includes / excludes.
        setIncludes(getDefaultIncludes());
        List<GlobPattern> excludes = new ArrayList<GlobPattern>();
        excludes.addAll(getSysFilesExcludes());
        excludes.addAll(getDownloadsExcludes());
        setExcludes(excludes);

        // Delete & create schedule tasks.
        Scheduler scheduler = Scheduler.getInstance();
        scheduler.create();
    }

    /**
     * Return default browse URL for the current computer.
     * 
     * @return
     */
    public String getBrowseUrl() {
        return BASE_URL + "/browse/" + getComputerName();
    }

    /**
     * Friently named used to represent the computer being backuped.
     * 
     * @return the computer name.
     */
    public String getComputerName() {
        return this.properties.getProperty(COMPUTERNAME);
    }

    /**
     * Return the exclude patterns used for the backup.
     * 
     * @return the list of pattern.
     */
    public List<GlobPattern> getExcludes() {
        try {
            return readPatterns(excludesFile);
        } catch (IOException e) {
            LOGGER.warn("error reading excludes patterns", e);
            return Collections.emptyList();
        }
    }

    /**
     * Return the include patterns used for the backup.
     * 
     * @return the list of pattern.
     */
    public List<GlobPattern> getIncludes() {
        try {
            return readPatterns(includesFile);
        } catch (IOException e) {
            LOGGER.warn("error reading includes patterns", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get the remote host used for the backup (SSH server).
     * 
     * @return the hostname.
     */
    public String getRemotehost() {
        return this.properties.getProperty(REMOTEHOST);
    }

    /**
     * Return the remote host to be used for SSH communication.
     * <p>
     * Current implementation return the same SSH server. In future, this implementation might changed to request the
     * web server for a specific URL.
     * 
     * @return
     */
    protected String getRemoteHost() {
        return DEFAULT_REMOTEHOST;
    }

    /**
     * Get the username used for the backup (username used to authentication with SSH server).
     * 
     * @return
     */
    public String getUsername() {
        return this.properties.getProperty(USERNAME);
    }

    /**
     * Register a new computer.
     * <p>
     * A successful link of this computer will generate a new configuration file.
     * <p>
     * This implementation will generate public and private keys for putty. The public key will be sent to minarca. The
     * computer name is added to the comments.
     * 
     * 
     * @param computername
     *            friendly name to represent this computer.
     * @throws APIException
     *             if the keys can't be created.
     * @throws IllegalAccessException
     *             if the computer name is not valid.
     */
    public void link(String username, String password, String computername) throws APIException {
        Validate.notNull(username);
        Validate.notNull(password);
        Validate.notEmpty(computername);
        Validate.isTrue(computername.matches("[a-zA-Z][a-zA-Z0-9\\-\\.]*"));

        /*
         * Generate the keys
         */
        LOGGER.debug("generating public and private key for {}", computername);
        File idrsaFile = new File(API.getConfigDirFile(), "id_rsa.pub");
        File identityFile = new File(API.getConfigDirFile(), "key.ppk");
        try {
            // Generate a key pair.
            KeyPair pair = Keygen.generateRSA();
            // Generate a simple id_rsa.pub file.
            Keygen.toPublicIdRsa((RSAPublicKey) pair.getPublic(), computername, idrsaFile);
            // Generate a Putty private key file.
            Keygen.toPrivatePuttyKey(pair, computername, identityFile);
        } catch (NoSuchAlgorithmException e) {
            throw new APIException("fail to generate the keys", e);
        } catch (IOException e) {
            throw new APIException("fail to generate the keys", e);
        } catch (InvalidKeyException e) {
            throw new APIException("fail to generate the keys", e);
        }

        /*
         * Share them via ssh.
         */
        LOGGER.debug("sending public key trought SSH");
        SSH ssh = SSH.getInstance(getRemoteHost(), username, password);
        ssh.sendPublicKey(idrsaFile);

        /*
         * Generate configuration file.
         */
        LOGGER.debug("saving configuration [{}][{}][{}]", computername, username, getRemoteHost());
        API.INSTANCE.setUsername(username);
        API.INSTANCE.setComputerName(computername);
        API.INSTANCE.setRemotehost(getRemoteHost());

    }

    /**
     * Internal method used to read patterns.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    private List<GlobPattern> readPatterns(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
        List<GlobPattern> list = new ArrayList<GlobPattern>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            try {
                list.add(new GlobPattern(line));
            } catch (IllegalArgumentException e) {
                // Swallow
                LOGGER.warn("invalid pattern [{}] discarded", line);
            }
        }
        reader.close();
        return list;
    }

    /**
     * Used to persist the configuration.
     * 
     * @throws IOException
     */
    private void save() throws IOException {
        Writer writer = new FileWriterWithEncoding(confFile, Charset.forName("ISO-8859-1"));
        this.properties.store(writer, "Backup configuration. Please do " + "not change this configuration file manually.");
        writer.close();
    }

    /**
     * Sets the computer name.
     * 
     * @param value
     * @throws APIException
     */
    public void setComputerName(String value) throws APIException {
        this.properties.setProperty(COMPUTERNAME, value);
        try {
            save();
        } catch (IOException e) {
            throw new APIException(_("fail to save config"), e);
        }
    }

    /**
     * Sets a new exclude patern list.
     * 
     * @param patterns
     * @throws APIException
     */
    public void setExcludes(List<GlobPattern> patterns) throws APIException {
        try {
            writePatterns(excludesFile, patterns);
        } catch (IOException e) {
            throw new APIException(_("fail to save config"), e);
        }
    }

    /**
     * Sets a new include pattern list.
     * 
     * @param patterns
     * @throws APIException
     */
    public void setIncludes(List<GlobPattern> patterns) throws APIException {
        try {
            writePatterns(includesFile, patterns);
        } catch (IOException e) {
            throw new APIException(_("fail to save config"), e);
        }
    }

    /**
     * Sets remote host.
     * 
     * @param value
     * @throws APIException
     */
    public void setRemotehost(String value) throws APIException {
        this.properties.setProperty(REMOTEHOST, value);
        try {
            save();
        } catch (IOException e) {
            throw new APIException(_("fail to save config"), e);
        }
    }

    /**
     * Sets the username.
     * 
     * @param value
     * @throws APIException
     */
    public void setUsername(String value) throws APIException {
        this.properties.setProperty(USERNAME, value);
        try {
            save();
        } catch (IOException e) {
            throw new APIException(_("fail to save config"), e);
        }
    }

    /**
     * Used to unlink this computer.
     * 
     * @throws APIException
     */
    public void unlink() throws APIException {
        // To unlink, delete the configuration and schedule task.
        setComputerName(null);
        setUsername(null);
        setRemotehost(null);

        // Delete task
        Scheduler scheduler = Scheduler.getInstance();
        scheduler.delete();
    }

    /**
     * Internal method used to write the patterns into a file.
     * 
     * @param file
     * @param pattern
     * @throws IOException
     */
    private void writePatterns(File file, List<GlobPattern> pattern) throws IOException {
        FileWriterWithEncoding writer = new FileWriterWithEncoding(file, "ISO-8859-1");
        for (GlobPattern line : pattern) {
            writer.append(line.value());
            writer.append(SystemUtils.LINE_SEPARATOR);
        }
        writer.close();
    }
}
