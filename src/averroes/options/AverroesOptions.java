/*******************************************************************************
 * Copyright (c) 2015 Karim Ali and Ondřej Lhoták.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Karim Ali - initial API and implementation and/or initial documentation
 *******************************************************************************/
package averroes.options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import averroes.exceptions.AverroesException;
import probe.ObjectManager;
import probe.ProbeClass;
import soot.SootClass;

/**
 * A class that holds all the properties required by Averroes to run. For the
 * possible values of each property, you can consult the accompanying
 * averroes.properties.sample file or the online tutorial at {@link http
 * ://karimali.ca/averroes}
 * 
 * @author Karim Ali
 * 
 */
public final class AverroesOptions {

	private static List<String> dynamicClasses = null;

	private static Option applicationRegex = Option.builder("r").longOpt("application-regex")
			.desc("a list of regular expressions for application packages or classes separated by File.pathSeparator")
			.hasArg().argName("regex").required().build();

	private static Option mainClass = Option.builder("m").longOpt("main-class")
			.desc("the main class that runs the application when the program executes").hasArg().argName("class")
			.required(false).build();

	private static Option applicationJars = Option.builder("a").longOpt("application-jars")
			.desc("a list of the application JAR files separated by File.pathSeparator").hasArg().argName("path")
			.required().build();

	private static Option libraryJars = Option.builder("l").longOpt("library-jars")
			.desc("a list of the JAR files for library dependencies separated by File.pathSeparator").hasArg()
			.argName("path").required(false).build();

	private static Option dynamicClassesFile = Option
			.builder("d")
			.longOpt("dynamic-classes-file")
			.desc("a file that contains a list of classes that are loaded dynamically by Averroes (e.g., classes instantiated through reflection)")
			.hasArg().argName("file").required(false).build();

	private static Option tamiflexFactsFile = Option.builder("t").longOpt("tamiflex-facts-file")
			.desc("a file that contains reflection facts generated for this application in the TamiFlex format")
			.hasArg().argName("file").required(false).build();

	private static Option outputDirectory = Option.builder("o").longOpt("output-directory")
			.desc("the directory to which Averroes will write any output files/folders.").hasArg().argName("directory")
			.required().build();

	private static Option jreDirectory = Option.builder("j").longOpt("java-runtime-directory")
			.desc("the directory that contains the Java runtime environment that Averroes should model").hasArg()
			.argName("directory").required(false).build();
	
	private static Option platformDirectory = Option.builder("p").longOpt("android-platform-directory")
			.desc("path to the android platforms directory that contains android.jar file").hasArg().argName("directory").required(false).build();

	private static Option help = Option.builder("h").longOpt("help").desc("print out this help message").hasArg(false)
			.required(false).build();

	private static Options options = new Options().addOption(applicationRegex).addOption(mainClass)
			.addOption(applicationJars).addOption(libraryJars).addOption(dynamicClassesFile)
			.addOption(tamiflexFactsFile).addOption(outputDirectory).addOption(platformDirectory).addOption(jreDirectory).addOption(help);

	private static CommandLine cmd;
	/**
	 * Documents, whether an android or java application is processed
	 */
	private static boolean android = false;


	/**
	 * Process the input arguments of Averroes.
	 * 
	 * @param args
	 * @throws AverroesException 
	 */
	public static void processArguments(String[] args) throws AverroesException {
		try {
			// If the application input is an Android app there will be only one apk.
			// Also, it's not as easy as with an apk (opposed to a jar) to extract class files.
			// In case of a java application, we iterate twice over the application inputs.
			// This is due to easier exception management (we can't pass the exception off of a lambda expression).
			
			cmd = new DefaultParser().parse(options, args);
			for (String s: getApplicationJars()) {
				if (s.endsWith(".apk")) {
					setAndroid(true);
				}	
			}
			if (getApplicationJars().size() > 1 && isAndroid()) {
				throw new AverroesException("Mutliple application archives detected while in Android mode. Only 1 apk is allowed.", new Throwable());	
			}
			
			// if it as an android app, the path to the platform directory has to be specified
			if(isAndroid()) {
				
				if(!cmd.hasOption(platformDirectory.getOpt())) {
					throw new AverroesException("missing option p : path to android-platform directory", new Throwable());
						
				}

			}
			//If it is a java application jre directory and main class need to be specified. 
			else
			{
				if(!(cmd.hasOption(jreDirectory.getOpt()) && cmd.hasOption(mainClass.getOpt()))) {
					throw new AverroesException("missing option j and/or m ", new Throwable());
						
				}
			}

			// Do we need to print out help messages?
			if (cmd.hasOption(help.getOpt())) {
				help();
			}
		} catch (AverroesException |ParseException e) {
			e.printStackTrace();
			help();
		}
	}

	/**
	 * Print out some help information.
	 */
	private static void help() {
		new HelpFormatter().printHelp("jar -jar averroes.jar", "", options, "", true);
		System.exit(0);
	}

	/**
	 * The list of application packages or classes separated by
	 * {@link File#pathSeparator}.
	 * 
	 * @return
	 */
	public static List<String> getApplicationRegex() {
		return Arrays.asList(cmd.getOptionValue(applicationRegex.getOpt()).split(File.pathSeparator));
	}
	/**
	 * A regex pattern that is usable by a java.regex.Matcher
	 * TODO: Refactor
	 * @return
	 */
	public static String getEscapedApplicationRegex() {
		List<String> appRegex = AverroesOptions.getApplicationRegex();
		StringBuilder pattern = new StringBuilder();
		
		for (String s: appRegex) {
			pattern.append("(");
			pattern.append(s);
			pattern.append(")|");
		}
		
		pattern.deleteCharAt(pattern.length()-1);
		String patternString = pattern.toString();
		patternString = patternString.replaceAll("\\.", "\\\\.");
		patternString = patternString.replaceAll("\\*+", "\\.*");	

		return patternString;
	}
	/**
	 * The main class that runs the application when the program executes.
	 * 
	 * @return
	 */
	public static String getMainClass() {
		return cmd.getOptionValue(mainClass.getOpt());
	}

	/**
	 * The list of the application JAR files separated by
	 * {@link File#pathSeparator}.
	 * 
	 * @return
	 */
	public static List<String> getApplicationJars() {
		return Arrays.asList(cmd.getOptionValue(applicationJars.getOpt()).split(File.pathSeparator));
	}

	/**
	 * The list of the library JAR files separated by {@link File#pathSeparator}
	 * 
	 * @return
	 */
	public static List<String> getLibraryJarFiles() {
		return Arrays.asList(cmd.getOptionValue(libraryJars.getOpt(), "").split(File.pathSeparator));
	}

	/**
	 * Is support for dynamic classes enabled?
	 * 
	 * @return
	 */
	public static boolean isDynamicClassesEnabled() {
		return cmd.hasOption(dynamicClassesFile.getOpt());
	}

	/**
	 * Get the names of classes that might be dynamically loaded by the input
	 * program.
	 * 
	 * @return
	 */
	public static List<String> getDynamicClasses() throws IOException {
		if (dynamicClasses == null) {
			dynamicClasses = new ArrayList<String>();

			if (isDynamicClassesEnabled()) {
				BufferedReader in = new BufferedReader(new FileReader(cmd.getOptionValue(dynamicClassesFile.getOpt())));
				String line;
				while ((line = in.readLine()) != null) {
					dynamicClasses.add(line);
				}
				in.close();
			}
		}

		return dynamicClasses;
	}

	/**
	 * Get the class names of the dynamic library classes.
	 * 
	 * @return
	 * @throws IOException
	 */
	public static List<String> getDynamicLibraryClasses() throws IOException {
		return getDynamicClasses().stream().filter(AverroesOptions::isLibraryClass).collect(Collectors.toList());
	}

	/**
	 * Get the class names of the dynamic application classes.
	 * 
	 * @return
	 * @throws IOException
	 */
	public static List<String> getDynamicApplicationClasses() throws IOException {
		return getDynamicClasses().stream().filter(AverroesOptions::isApplicationClass).collect(Collectors.toList());
	}

	/**
	 * Is support for TamiFlex facts enabled?
	 * 
	 * @return
	 */
	public static boolean isTamiflexEnabled() {
		return cmd.hasOption(tamiflexFactsFile.getOpt());
	}

	/**
	 * Get the files that contains the reflection facts in the TamiFlex format
	 * for this program.
	 * 
	 * @return
	 */
	public static String getTamiflexFactsFile() {
		return cmd.getOptionValue(tamiflexFactsFile.getOpt(), "");
	}

	/**
	 * The directory to which Averroes will write any output files/folders.
	 * 
	 * @return
	 */
	public static String getOutputDirectory() {
		return cmd.getOptionValue(outputDirectory.getOpt());
	}

	/**
	 * The version for the Java runtime environment to be used. This can one of
	 * (1.4, 1.6, 1.7, 1.8, system).
	 * 
	 * @return
	 */
	public static String getJreDirectory() {
		return cmd.getOptionValue(jreDirectory.getOpt());
	}

	/**
	 * Check if a class belongs to the application, based on the
	 * {@value #APPLICATION_INCLUDES} property.
	 * 
	 * @param probeClass
	 * @return
	 */
	public static boolean isApplicationClass(ProbeClass probeClass) {
		for (String entry : getApplicationRegex()) {
			/*
			 * 1. If the entry ends with .* then this means it's a package. 2.
			 * If the entry ends with .** then it's a super package. 3. If the
			 * entry is **, then it's the default package. 4. Otherwise, it's
			 * the full class name.
			 */
			if (entry.endsWith(".*")) {
				String pkg = entry.replace(".*", "");
				if (probeClass.pkg().equalsIgnoreCase(pkg)) {
					return true;
				}
			} else if (entry.endsWith(".**")) {
				String pkg = entry.replace("**", "");
				if (probeClass.toString().startsWith(pkg)) {
					return true;
				}
			} else if (entry.equalsIgnoreCase("**") && probeClass.pkg().isEmpty()) {
				return true;
			} else if (entry.equalsIgnoreCase(probeClass.toString())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if a class belongs to the application, based on the
	 * {@link #applicationRegex} option.
	 * 
	 * @param className
	 * @return
	 */
	public static boolean isApplicationClass(String className) {
		return isApplicationClass(ObjectManager.v().getClass(className));
	}

	/**
	 * Check if a class belongs to the application, based on the
	 * {@link #applicationRegex} option.
	 * 
	 * @param sootClass
	 * @return
	 */
	public static boolean isApplicationClass(SootClass sootClass) {
		return isApplicationClass(sootClass.getName());
	}

	/**
	 * Check if a class belongs to the library (i.e., not an application class).
	 * 
	 * @param className
	 * @return
	 */
	public static boolean isLibraryClass(String className) {
		return !isApplicationClass(className);
	}
	
    /**
     * Checks if android app is being processed
     * @return
     */
	public static boolean isAndroid() {		
		return android;
	}
	
	/**
	 * 
	 * @param android  true if android app, false otherwise.
	 */
	public static void setAndroid(boolean android) {
		AverroesOptions.android = android;
	}
	
	/**
	 * Returns the path to the android platform directory 
	 * @return 
	 */
	public static String getAndroidJar() {
		return cmd.getOptionValue(platformDirectory.getOpt());
	}
	
	/**
	 * returns the path to the android apk file. 	
	 * @return
	 */
	public static String getApk() {
		if (isAndroid()) return getApplicationJars().get(0);
		
		return null;
	}
	public static String getApkJar() {
		if (isAndroid()) {
			String apk = getApk();
			return apk.replace(".apk", ".jar");	
		}
		return null;
	}
}
