package averroes.android;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jf.dexlib2.dexbacked.raw.ClassDefItem;
import org.jf.dexlib2.dexbacked.raw.MethodIdItem;
import org.jf.dexlib2.dexbacked.raw.RawDexFile;
import org.xmlpull.v1.XmlPullParserException;

import soot.options.Options;
import averroes.exceptions.AverroesException;
import averroes.options.AverroesOptions;
import averroes.soot.Hierarchy;
import averroes.util.DexUtils;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.coffi.Util;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

/**
 * Sets up Averroes such that it works with Android applications. Specifically,
 * this class uses flowdroid to create the dummy main method.
 * 
 * 
 * @author Eshna Sengupta, Michael Appel
 *
 */

public class SetupAndroid {

	private static SetupAndroid instance;
	private ProcessManifest processMan;
	private final int apiVersion;
	private String apkFileLocation;
	private String androidJars;
	private SootMethod dummyMain = null;
	private RawDexFile rawDex;

	public static SetupAndroid v() {
		if (instance == null) {
			try {
				instance = new SetupAndroid();
			} catch (AverroesException ex) {
				ex.printStackTrace();
			}
		}
		return instance;
	}

	/**
	 * Constructor
	 * 
	 * @throws AverroesException
	 *             if API version is not found
	 */

	private SetupAndroid() throws AverroesException {
		apkFileLocation = AverroesOptions.getApk();
		androidJars = AverroesOptions.getAndroidJar();
		
		try {
			processMan = new ProcessManifest(apkFileLocation);
		}
		catch(Exception ex) {
			ex.printStackTrace();}
		

		apiVersion = Scene.v().getAndroidAPIVersion();
		if (apiVersion == -1) {
			throw new AverroesException("Couldn't find the Android API version", new Throwable());
		}

	}

	/**
	 * Uses flowdroid to create the dummy main method. 
	 * @return
	 * @throws XmlPullParserException 
	 * @throws IOException 
	 */
	public SootMethod getDummyMainMethod() throws IOException, XmlPullParserException {
		if (dummyMain != null) {
			return dummyMain;
		}
        InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
		//config.getAnalysisFileConfig().setTargetAPKFile(apkFileLocation);
		//config.setAndroidPlatformDir(androidJars);
		//config.setCallbackAnalyzer(CallbackAnalyzer.Fast);
		SetupApplication app = new SetupApplication(androidJars,apkFileLocation);
		//app.setConfig(config);
		app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
        soot.G.reset();

        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
        Options.v().set_android_jars(androidJars);
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_output_format(Options.output_format_class);
        Options.v().setPhaseOption("cg.spark", "on");


        Scene.v().loadNecessaryClasses();

        dummyMain = app.getEntryPointCreator().createDummyMain();
        Options.v().set_main_class(dummyMain.getSignature());
        Scene.v().setEntryPoints(Collections.singletonList(dummyMain));
		//dummyMain.getDeclaringClass().setSuperclass(Hierarchy.v().getJavaLangObject());
		return dummyMain;
	}
	

	public RawDexFile getRawDex() {
		// needs to be done after the constructor, hence the field is initialized here
		if (rawDex == null) {
			try {
				rawDex = DexUtils.getRawDex(new File(apkFileLocation), null);
			} catch (IOException ioEx) {
				ioEx.printStackTrace();
			}
		}
		return rawDex;
	}

	public int getReferencedApplicationClassCount() {
		// TODO: Refactor
		String[] classes = ClassDefItem.getClasses(getRawDex());
		List<String> result = new LinkedList<>();

		String patternString = AverroesOptions.getEscapedApplicationRegex();
		Pattern p = Pattern.compile(patternString);

		for (String s : classes) {
			Type jimpleType = Util.v().jimpleTypeOfFieldDescriptor(s);
			Matcher m = p.matcher(jimpleType.toString());
			while (m.find()) {
				String match = m.group();
				result.add(match);
			}
		}

		/*
		 * for (String s: result) { System.out.println(s); }
		 */
		return result.size();
	}

	public int getReferencedApplicationMethodCount() {
		String[] methods = MethodIdItem.getMethods(getRawDex());

		List<String> result = new LinkedList<>();

		String patternString = AverroesOptions.getEscapedApplicationRegex();
		Pattern p = Pattern.compile(patternString);

		for (String s : methods) {
			String[] clazzAndMethod = s.split("-");
			Type jimpleType = Util.v().jimpleTypeOfFieldDescriptor(clazzAndMethod[0]);
			Matcher m = p.matcher(jimpleType.toString());
			while (m.find()) {
				String match = m.group();
				result.add(s);
			}
		}
		/*
		 * for (String s: result) { System.out.println(s); }
		 */
		return result.size();
	}

	public int getApiVersion() {
		return apiVersion;
	}

	public String getApkFileLocation() {
		return apkFileLocation;
	}
	

}
