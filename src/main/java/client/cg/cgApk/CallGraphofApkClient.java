package main.java.client.cg.cgApk;

import main.java.base.MyConfig;
import main.java.client.BaseClient;
import main.java.client.manifest.ManifestClient;

/**
 * Analyzer Class
 * 
 * @author hanada
 * @version 2.0
 */
public class CallGraphofApkClient extends BaseClient {

	@Override
	protected void clientAnalyze() {
		if (!MyConfig.getInstance().isManifestAnalyzeFinish()) {
			new ManifestClient().start();
			MyConfig.getInstance().setManifestAnalyzeFinish(true);
		}
		CgConstructor cgAnalyzer = new CgConstructor();
		cgAnalyzer.analyze();
		CgModify cgModify = new CgModify();
		cgModify.analyze();
	}

	@Override
	public void clientOutput() {
		/** call graph, if needed, open output**/
//		String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
//				+ File.separator;
//		FileUtils.createFolder(summary_app_dir + ConstantUtils.CGFOLDETR);
//		CgClientOutput.writeCG(summary_app_dir + ConstantUtils.CGFOLDETR,
//				Global.v().getAppModel().getAppName()+"_cg.txt", Global.v().getAppModel().getCg());
	}

}