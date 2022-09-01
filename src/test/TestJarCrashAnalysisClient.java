package test;

import main.java.MainClass;
import main.java.base.MyConfig;
import soot.options.Options;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestJarCrashAnalysisClient {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        MainClass.startAnalyze();
        System.out.println("Finish...\n");
        System.exit(0);

    }

    private void setArgs() {
        String path;
        path = "apk\\";
        String name;
        name = "facebook-android-sdk-894.jar";

        String client = "JarCrashAnalysisClient";

        MyConfig.getInstance().setAppName(name);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setResultWarpperFolder("results" + File.separator);
        MyConfig.getInstance().setResultFolder(MyConfig.getInstance().getResultWarpperFolder()+ "output" + File.separator);
        MyConfig.getInstance().setTimeLimit(10);
        MyConfig.getInstance().setAndroidJar("E:\\AndroidSDK\\android-sdk-windows-new\\platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_only_class);
        MyConfig.getInstance().setCrashInfoFilePath("Files\\crashInfo.json");
        MyConfig.getInstance().setStrategy("");
        MyConfig.getInstance().setAndroidOSVersion("6.0");
    }

}
