package main.java.client.dataAnalysis;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.base.Analyzer;
import main.java.base.MyConfig;
import main.java.client.crash.CrashAnalysis;
import main.java.client.crash.CrashInfo;
import main.java.client.crash.Strategy;
import main.java.client.exception.*;
import main.java.utils.FileUtils;
import main.java.utils.PrintUtils;
import main.java.utils.SootUtils;
import soot.toolkits.scalar.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author hanada
 * @Date 2022/6/24 10:22
 * @Version 1.0
 */
public class ExceptionMather {
    List<CrashInfo> crashInfoList = new ArrayList<>();
    String[] versions = {"2.3", "4.4", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0", "11.0", "12.0"};
    Map<String, JSONObject> version2JsonStr = new HashMap<String,JSONObject>();

    public void analyze() {
        readAllCrashInfo();
        System.out.println("readCrashInfo Finish...");

        exceptionOracleAnalysis();
//        getExceptionOfCrashInfoWithGivenVersion();
        System.out.println("getExceptionOfCrashInfo Finish...");

    }

    private void exceptionOracleAnalysis() {
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"ETSCorrectness.txt", "", false);
        System.out.println("write to "+ MyConfig.getInstance().getResultFolder() +"ETSCorrectness.txt");
        for(CrashInfo crashInfo: crashInfoList) {
            String str= crashInfo.getId()+"\t"+crashInfo.getSignaler()+"\t";
            String[] versionTypes = new String[versions.length];
            String[] versionTypeCandis = new String[versions.length];
            String[] targetMethodNames = new String[versions.length];
            int i = 0;
            for (String version : versions) {
                Pair<String, String> pair = getExceptionWithGivenVersion(crashInfo, version, true);
                versionTypes[i] = pair.getO1();
                targetMethodNames[i] = pair.getO2();
                if (versionTypes[i].equals("notFound")) {
                    Pair<String, String> pair2 = getExceptionWithGivenVersion(crashInfo, version, false);
                    versionTypeCandis[i] = pair2.getO1();
                    targetMethodNames[i] = pair2.getO2();
                }
                i++;
            }
            String targetVerStr = getTargetType(versionTypes);
            if (targetVerStr ==null) {
                targetVerStr = getTargetType(versionTypeCandis);
            }
            if (targetVerStr ==null)
                targetVerStr = RelatedVarType.Unknown.toString();
            str += targetVerStr +"\t"+crashInfo.getRelatedVarTypeOracle()+"\t"+ (targetVerStr.equals(crashInfo.getRelatedVarTypeOracle().toString()))+"\n";
            System.out.println(str);
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"ETSCorrectness.txt", str, true);
        }
    }

    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private Pair<String,String> getExceptionWithGivenVersion(CrashInfo crashInfo, String version, boolean classFilter) {
        MyConfig.getInstance().setExceptionFilePath("Files"+File.separator+"android"+version+File.separator+"exceptionInfo"+File.separator);
        String fn = MyConfig.getInstance().getExceptionFilePath()+"summary"+ File.separator+ "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) return new Pair<>("noFile",crashInfo.getSignaler());
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (Object method : methods) {
            JSONObject jsonObject = (JSONObject) method;
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            if (!classFilter || crashInfo.getSignaler().equals(exceptionInfo.getSootMethodName())) {
                exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (m.matches()) {
                    if(!classFilter) {
                        String str = exceptionInfo.getExceptionMsg();
                        str = str.replace("[\\s\\S]*", "");
                        str = str.replace("\\Q", "");
                        str = str.replace("\\E", "");
                        if (str.length() < 3)
                            continue;
                    }
                    if (jsonObject.getString("relatedVarType") != null) {
                        return new Pair<>(jsonObject.getString("relatedVarType"), exceptionInfo.getSootMethodName());
                    }else{
                        return new Pair<>("Unknown", exceptionInfo.getSootMethodName());
                    }
                }
            }
        }
        return new Pair<>("notFound",crashInfo.getSignaler());
    }

    private String getTargetType(String[] versionType) {
        int paraAndField =0 , fieldOnly =0 ,parameterOnly =0 , overrideMissing = 0;
        for(String relatedVarType: versionType) {
            if(relatedVarType ==null) continue;
            if (relatedVarType.equals(RelatedVarType.ParaAndField.toString())) paraAndField++;
            if (relatedVarType.equals(RelatedVarType.Field.toString())) fieldOnly++;
            if (relatedVarType.equals(RelatedVarType.Parameter.toString())) parameterOnly++;
            if (relatedVarType.equals(RelatedVarType.Empty.toString())) overrideMissing++;
        }
        String choice = RelatedVarType.Unknown.toString();
        if(paraAndField + parameterOnly + fieldOnly + overrideMissing ==0)
            choice = RelatedVarType.Unknown.toString();
        else if(paraAndField >= parameterOnly && paraAndField >= fieldOnly && paraAndField >= overrideMissing)
            choice =  RelatedVarType.ParaAndField.toString();
        else if(parameterOnly >= fieldOnly && parameterOnly >= paraAndField && parameterOnly >= overrideMissing)
            choice = RelatedVarType.Parameter.toString();
        else if(fieldOnly >= parameterOnly && fieldOnly >= paraAndField && fieldOnly >= overrideMissing)
            choice =  RelatedVarType.Field.toString();
        else if(overrideMissing >= parameterOnly && overrideMissing >= paraAndField && overrideMissing >= fieldOnly)
            choice = RelatedVarType.Empty.toString();
        for(int i = versionType.length-1; i>=0; i--) {
            if (versionType[i] != null && versionType[i].equals(choice))
                return choice;
        }
        return null;
    }
    private void getExceptionOfCrashInfoWithGivenVersion() {
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt", "", false);
        System.out.println("write to "+ MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt");
        for(CrashInfo crashInfo: crashInfoList) {
            String str= crashInfo.getId()+"\t"+crashInfo.getSignaler()+"\t";
            System.out.println("Analysis crash "+ str);
            for (String version : versions) {
                String relatedVarType = getExceptionWithGivenVersion(crashInfo, version);
                str+=relatedVarType+"\t";
            }
//            System.out.println(str);
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt", str+"\n", true);
        }
    }


    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private String getExceptionWithGivenVersion(CrashInfo crashInfo, String version) {
        String jsonString = "";
        JSONObject wrapperObject = null;
        if(version2JsonStr.containsKey(version)){
            wrapperObject = version2JsonStr.get(version);
        }else {
            MyConfig.getInstance().setExceptionFilePath("Files"+File.separator +"android" + version  +File.separator+ "exceptionInfo" +File.separator);
            String fn = MyConfig.getInstance().getExceptionFilePath() + "summary" + File.separator + "exception.json";
            jsonString = FileUtils.readJsonFile(fn);
            wrapperObject = (JSONObject) JSONObject.parse(jsonString);
            version2JsonStr.put(version,wrapperObject);
        }

        if(wrapperObject==null) return "noFile";
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            if(crashInfo.getSignaler().equals(exceptionInfo.getSootMethodName())){
                exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (exceptionInfo.getExceptionMsg().equals(crashInfo.getMsg()) || m.matches()) {
                    crashInfo.setExceptionInfo(exceptionInfo);
                    if(exceptionInfo!=null && jsonObject.getString("relatedVarType")!=null) {
                        return jsonObject.getString("relatedVarType");
                    }
                }
            }
        }
        return "unknown";
    }



    /**
     * readCrashInfo from CrashInfoFile
     */
    private void readAllCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        System.out.println("readCrashInfo::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (int i = 0 ; i < jsonArray.size();i++){
            JSONObject jsonObject = (JSONObject)jsonArray.get(i);
            CrashInfo crashInfo = new CrashInfo();
            crashInfoList.add(crashInfo);
            crashInfo.setIdentifier(jsonObject.getString("identifier"));
            crashInfo.setReal(jsonObject.getString("real"));
            crashInfo.setException(jsonObject.getString("exception"));
            crashInfo.setTrace(jsonObject.getString("trace"));
            crashInfo.setBuggyApi(jsonObject.getString("buggyApi"));
            crashInfo.setMsg(jsonObject.getString("msg").trim());
            crashInfo.setRealCate(jsonObject.getString("realCate"));
            crashInfo.setCategory(jsonObject.getString("category"));
            if(jsonObject.getString("fileName")!=null)
                crashInfo.setId(jsonObject.getString("fileName"));
            else
                crashInfo.setId(crashInfo.getIdentifier()+"-"+ jsonObject.getString("id"));
            crashInfo.setReason(jsonObject.getString("reason"));
            crashInfo.setMethodName(crashInfo.getTrace().get(0));

            if(jsonObject.getString("relatedVarType")!=null)
                crashInfo.setRelatedVarTypeOracle(RelatedVarType.valueOf(jsonObject.getString("relatedVarType")));
            if(jsonObject.getString("relatedCondType")!=null)
                crashInfo.setRelatedCondTypeOracle(RelatedCondType.valueOf(jsonObject.getString("relatedCondType")));

            JSONObject callerOfSingnlar2SourceVar = jsonObject.getJSONObject("callerOfSingnlar2SourceVar");
            if (callerOfSingnlar2SourceVar != null) {
                for (String key : callerOfSingnlar2SourceVar.keySet()) {
                    String[] ids = ((String) callerOfSingnlar2SourceVar.get(key)).split(",");
                    for (String id : ids)
                        crashInfo.addCallerOfSingnlar2SourceVarOracle(SootUtils.getMethodSimpleNameFromSignature(key), Integer.valueOf(id));
                }
            }
        }
    }
}
