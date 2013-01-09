/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.cli_plugin;

import org.apache.log4j.Logger;
import org.broad.igv.DirectoryManager;
import org.broad.igv.PreferenceManager;
import org.broad.igv.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class used to parse a cli_plugin specification file (currently XML).
 * <p/>
 * User: jacob
 * Date: 2012-Aug-03
 */
public class PluginSpecReader {

    private static Logger log = Logger.getLogger(PluginSpecReader.class);

    protected String specPath;
    protected Document document;

    public static final String CUSTOM_PLUGINS_FILENAME = "custom_plugins.txt";
    public static final String BUILTIN_PLUGINS_FILENAME = "builtin_plugins.txt";

    public static final String COMMAND = "command";
    public static final String CMD_ARG = "cmd_arg";

    /**
     * List of plugins tha IGV knows about
     */
    private static List<PluginSpecReader> pluginList;

    /**
     * List of tools described contained in this pluginSpec
     */
    private List<Tool> tools;

    private PluginSpecReader(String path) {
        this.specPath = path;
    }

    /**
     * Create a new reader. Returns null if
     * the input path does not represent a valid cli_plugin spec,
     * or if there was any other problem parsing the document
     *
     * @param path
     * @return
     */
    public static PluginSpecReader create(String path) {
        PluginSpecReader reader = new PluginSpecReader(path);
        if (!reader.parseDocument()) return null;
        return reader;
    }

    public static boolean isToolPathValid(String execPath) {
        execPath = FileUtils.findExecutableOnPath(execPath);
        File execFile = new File(execPath);
        boolean pathValid = execFile.isFile();
        if (pathValid && !execFile.canExecute()) {
            log.error(execPath + " exists but is not executable. ");
            pathValid = false;
        }

        return pathValid;
    }

    public String getSpecPath() {
        return specPath;
    }

    public String getId() {
        return document.getDocumentElement().getAttribute("id");
    }

    private boolean parseDocument() {
        boolean success = false;
        //We want to accept either a path within the JAR file (getResource),
        //or external path. Also we want to use builder.parse(String) so
        //that we can use relative links for DTD spec
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            URL url = getClass().getResource(specPath);
            String uri = null;
            if (url == null) {
                uri = FileUtils.getAbsolutePath(specPath, (new File(".")).getAbsolutePath());
            } else {
                uri = url.toString();
            }
            document = builder.parse(uri);
            success = document.getDocumentElement().getTagName().equals("cli_plugin");
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return success;
    }

    public List<Tool> getTools() {
        if(tools == null){
            tools = PluginSpecReader.<Tool>unmarshalElementsByTag(document.getDocumentElement(), "tool");
        }
        return tools;
    }

    private static <T> List<T> unmarshalElementsByTag(Element topElement, String tag){
        NodeList nodes = topElement.getElementsByTagName(tag);
        List<T> outNodes = new ArrayList<T>(nodes.getLength());
        for (int nn = 0; nn < nodes.getLength(); nn++) {
            outNodes.add(PluginSpecReader.<T>unmarshal(nodes.item(nn)));
        }
        return outNodes;
    }

    public static List<PluginSpecReader> getPlugins() {
        if (pluginList == null) {
            pluginList = generatePluginList();
        }
        return pluginList;
    }

    /**
     * Return a list of cli_plugin specification files present on the users computer.
     * Checks the IGV installation directory (jar) as well as IGV data directory
     *
     * @return
     */
    private static List<PluginSpecReader> generatePluginList() {
        List<PluginSpecReader> readers = new ArrayList<PluginSpecReader>();
        //Guard against loading cli_plugin multiple times. May want to reconsider this,
        //and override equals of PluginSpecReader
        Set<String> pluginIds = new HashSet<String>();

        try {
            File[] checkDirs = new File[]{
                    DirectoryManager.getIgvDirectory(), new File(FileUtils.getInstallDirectory()),
                    new File(".")
            };
            for (File checkDir : checkDirs) {
                File plugDir = new File(checkDir, "plugins");
                File[] possPlugins = plugDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".xml");
                    }
                });

                if (possPlugins == null) {
                    possPlugins = new File[0];
                }

                List<String> possPluginsList = new ArrayList<String>(possPlugins.length);

                for (File fi : possPlugins) {
                    possPluginsList.add(fi.getAbsolutePath());
                }

                //When a user adds a cli_plugin, we store the path here
                File customFile = new File(checkDir, CUSTOM_PLUGINS_FILENAME);
                if (customFile.canRead()) {
                    BufferedReader br = new BufferedReader(new FileReader(customFile));
                    possPluginsList.addAll(getPluginPaths(br));
                }

                //Builtin plugins. Do these last so custom ones take precedence
                for (String pluginName : getBuiltinPlugins()) {
                    possPluginsList.add("resources/" + pluginName);
                }


                for (String possPlugin : possPluginsList) {
                    PluginSpecReader reader = PluginSpecReader.create(possPlugin);
                    if (reader != null && !pluginIds.contains(reader.getId())) {
                        readers.add(reader);
                        pluginIds.add(reader.getId());
                    }
                }


            }

        } catch (Exception e) {
            log.error(e);
            //Guess this user won't be able to use plugins
        }
        return readers;
    }

    static List<String> getBuiltinPlugins() throws IOException {
        InputStream contentsStream = PluginSpecReader.class.getResourceAsStream("resources/" + PluginSpecReader.BUILTIN_PLUGINS_FILENAME);
        BufferedReader inReader = new BufferedReader(new InputStreamReader(contentsStream));
        return getPluginPaths(inReader);
    }

    private static List<String> getPluginPaths(BufferedReader reader) throws IOException {
        String line;
        List<String> pluginPaths = new ArrayList<String>(3);
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            pluginPaths.add(line);
        }
        return pluginPaths;
    }

    /**
     * @param absolutePath Full path (can be URL) to cli_plugin
     */
    public static void addCustomPlugin(String absolutePath) throws IOException {
        File outFile = new File(DirectoryManager.getIgvDirectory(), CUSTOM_PLUGINS_FILENAME);

        outFile.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
        writer.write(absolutePath);
        writer.write("\n");
        writer.flush();
        writer.close();

        pluginList = generatePluginList();
    }

    public String getName() {
        return document.getDocumentElement().getAttribute("name");
    }

    /**
     * Check the preferences for the tool path, using default from
     * XML spec if necessary
     *
     * @param tool
     * @return
     */
    public String getToolPath(Tool tool) {
        //Check settings for path, use default if not there
        String toolPath = PreferenceManager.getInstance().getToolPath(getId(), tool.name);
        if (toolPath == null) {
            toolPath = tool.defaultPath;
        }
        return toolPath;
    }


    private static JAXBContext jc = null;
    static JAXBContext getJAXBContext() throws JAXBException {
        if(jc == null){
            jc = JAXBContext.newInstance(Tool.class, Command.class, Argument.class, Parser.class);
        }
        return jc;
    }


    static <T> T unmarshal(Node node) {
        try {
            Unmarshaller u = getJAXBContext().createUnmarshaller();
            u.setListener(ToolListener.getInstance());
            //TODO change schema to W3C
            //u.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(myFile);
            return (T) u.unmarshal(node);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Each tool may contain default settings for each constituent command, we write
     * those settings into each command after unmarshalling
     */
    private static class ToolListener extends Unmarshaller.Listener{
        private static ToolListener instance;

        @Override
        public void afterUnmarshal(Object target, Object parent) {
            super.afterUnmarshal(target, parent);
            if(target instanceof Tool){
                Tool tool = (Tool) target;
                boolean hasDefault = tool.defaultSettings != null;
                if(hasDefault){
                    for(Command command: ((Tool) target).commandList){
                        if(command.argumentList == null) command.argumentList = tool.defaultSettings.argumentList;
                        if(command.parser == null) command.parser = tool.defaultSettings.parser;
                    }
                }
            }

        }

        public static Unmarshaller.Listener getInstance() {
            if(instance == null) instance = new ToolListener();
            return instance;
        }
    }

    /**
     * Represents an individual command line tool/executable, e.g. bedtools
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class Tool{
        @XmlAttribute public String name;
        @XmlAttribute public String defaultPath;
        @XmlAttribute public boolean visible;
        @XmlAttribute public String toolUrl;

        /**
         * Contains the default settings for commands, including
         * parser and arguments.
         */
        @XmlElement(name = "default") private Command defaultSettings;

        @XmlElement(name = "command") public List<Command> commandList;
    }
    /**
     * Description of how to parse each line read back from command line tool
     * User: jacob
     * Date: 2012-Dec-27
     */
    @XmlAccessorType(XmlAccessType.NONE)
    public static class Parser {
        @XmlAttribute boolean strict;
        @XmlAttribute String format;
        @XmlAttribute String libs;
        @XmlAttribute String decodingCodec;
    }

    /**
     * Represents a single command to be applied to a tool. e.g. intersect
     */
    @XmlAccessorType(XmlAccessType.NONE)
    public static class Command{
        @XmlAttribute public String name;
        @XmlAttribute public String cmd = "";

        @XmlElement(name = "arg") public List<Argument> argumentList;
        @XmlElement public Parser parser;
    }


}
