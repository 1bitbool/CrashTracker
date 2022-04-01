package main.java.client.exception;

import com.google.common.collect.Lists;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.StringUtils;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.toolkits.scalar.ValueUnitPair;

import java.io.File;
import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/11 15:21
 * @Version 1.0
 */
public class ExceptionAnalyzer extends Analyzer {
    List<ExceptionInfo> exceptionInfoList;

    public ExceptionAnalyzer(StatisticResult ignoredResult) {
        super();
    }


    /**
     * true: not analyze
     * @param sootMethod
     * @return
     */
    private boolean filterMethod(SootMethod sootMethod) {
        List<String> mtds = new ArrayList<>();
        mtds.add("acquireReference");
        mtds.add("executeOnExecutor");
        mtds.add("throwIfClosedLocked");
        mtds.add("onDowngrade");
        mtds.add("bindServiceCommon");
        mtds.add("checkListener");
        mtds.add("forgetReceiverDispatcher");
        mtds.add("forgetServiceDispatcher");
        mtds.add("Spinner: void setAdapter");
        mtds.add(" startRecording");
        mtds.add(" readException(");
        mtds.add(" checkStartActivityResult(");
        mtds.add(" getText(");
        mtds.add(" setView(");
        mtds.add(" getString(");
        mtds.add(" bindString(");
        mtds.add(" missingDialog(");
        mtds.add(" checkRange(");
        mtds.add(" enqueueAction(");
        for(String tag: mtds){
            if (sootMethod.getSignature().contains(tag)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void analyze() {
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        int total = 0;
        for (SootClass sootClass : applicationClasses) {
            if(!sootClass.getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
            exceptionInfoList = new ArrayList<>();
            for (SootMethod sootMethod : sootClass.getMethods()) {
//                if(filterMethod(sootMethod)) continue;
                System.out.println(sootMethod.getSignature());
                if (sootMethod.hasActiveBody()) {
                    try {
                        Map<Unit, String> unit2Message = new HashMap<>();
                        Map<Unit,Local> unit2Value = getThrowUnitWithValue(sootMethod);
                        for(Map.Entry<Unit,Local> entry: unit2Value.entrySet()){
                            getThrowUnitWithMessage(unit2Message, sootMethod, entry.getKey(),entry.getValue());
                        }
                        for(Map.Entry<Unit,String> entry: unit2Message.entrySet()) {
                            creatNewExceptionInfo(sootMethod, entry.getKey(), entry.getValue());
                        }

                    } catch (Exception |  Error e) {
                        System.out.println("Exception |  Error:::" + sootMethod.getSignature());
                        e.printStackTrace();
                    }
                }
            }
            writeJsonForCurrentClass(sootClass);
            total += exceptionInfoList.size();
        }
        System.out.println("TotalCaught:::" + total);
    }

    /**
     * get throw units with value from a method
     */
    public Map<Unit,Local> getThrowUnitWithValue(SootMethod sootMethod){
        Map<Unit,Local> unit2Value = new HashMap<>();
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            if (unit instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) unit;
                Value throwValue = throwStmt.getOp();
                if (throwValue instanceof Local) {
                    unit2Value.put(unit,(Local) throwValue);
                }
            }
        }
        return  unit2Value;
    }

    /**
     * get throw units with message from a method
     */
    public void getThrowUnitWithMessage(Map<Unit, String> unit2Message, SootMethod sootMethod, Unit unit, Local localTemp){
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
        if (defsOfOps.size() == 0) return;
        Unit defOfLocal = defsOfOps.get(0);
        if (defOfLocal.equals(unit)) return;

        if (defOfLocal instanceof DefinitionStmt) {
            Value rightValue = ((DefinitionStmt)defOfLocal).getRightOp();
            if (rightValue instanceof NewExpr) {
                NewExpr newRightValue = (NewExpr) rightValue;
                String name = newRightValue.getBaseType().getSootClass().toString();
                unit2Message.put(unit,name);
            } else if (rightValue instanceof NewArrayExpr) {
                NewArrayExpr rightValue1 = (NewArrayExpr) rightValue;
                String s = rightValue1.getBaseType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            } else if (rightValue instanceof Local) {
                getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) rightValue);
            } else if (rightValue instanceof JCastExpr) {
                JCastExpr castExpr = (JCastExpr) rightValue;
                String s = castExpr.getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                } else {
                    Value value = castExpr.getOpBox().getValue();
                    if (value instanceof Local) {
                        getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) value);
                    }
                }
            } else if (rightValue instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) rightValue;
                Type returnType = invokeExpr.getMethod().getReturnType();
                if (returnType.toString().endsWith("Exception") || returnType.toString().equals("java.lang.Throwable")) {
                    unit2Message.put(unit,returnType.toString());
                }

            } else if (rightValue instanceof CaughtExceptionRef) {
                //todo
                //caught an Exception here
                //$r1 := @caughtexception;
            } else if (rightValue instanceof PhiExpr) {
                PhiExpr phiExpr = (PhiExpr) rightValue;
                for (ValueUnitPair arg : phiExpr.getArgs()) {
                    if (arg.getValue() instanceof Local) {
                        getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) arg.getValue());
                    }
                }
            } if (rightValue instanceof FieldRef) {
                FieldRef rightValue1 = (FieldRef) rightValue;
                String s = rightValue1.getField().getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            } else if (rightValue instanceof ParameterRef) {
                ParameterRef rightValue1 = (ParameterRef) rightValue;
                String s = rightValue1.getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            }  else if (rightValue instanceof ArrayRef) {
                ArrayRef rightValue1 = (ArrayRef) rightValue;
                Value value = rightValue1.getBaseBox().getValue();
                if (value instanceof Local) {
                    getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) value);
                }
            }
        }
    }

    /**
     * creat a New ExceptionInfo object and add content
     */
    private void creatNewExceptionInfo(SootMethod sootMethod, Unit unit, String exceptionName) {
//        System.out.println("creatNewExceptionInfo");
        ExceptionInfo exceptionInfo =  new ExceptionInfo(sootMethod, unit, exceptionName);
        getExceptionMessage(sootMethod, unit, exceptionInfo, new ArrayList<>());
        getExceptionCondition(sootMethod, unit, exceptionInfo, new HashSet<>());
        if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size() ==0) {
            RelatedMethod addMethod = new RelatedMethod(sootMethod.getSignature(), RelatedMethodSource.CALLER, 0);
            exceptionInfo.addRelatedMethodsInSameClass(addMethod);
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(), 1, RelatedMethodSource.CALLER, exceptionInfo.getRelatedValueIndex());
        }else if(exceptionInfo.getRelatedParamValues().size()==0 && exceptionInfo.getRelatedFieldValues().size()>0) {
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1,RelatedMethodSource.FIELD);
        }else if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size()>0){
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1, RelatedMethodSource.FIELD);
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(),1, RelatedMethodSource.CALLER, exceptionInfo.getRelatedValueIndex());
        }
        exceptionInfoList.add(exceptionInfo);
    }

    /**
     * getExceptionCaller
     * @param sootMethod
     * @param exceptionInfo
     */
    private void getExceptionCallerByParam(SootMethod sootMethod, ExceptionInfo exceptionInfo,
                                           Set<SootMethod> callerHistory, int depth, RelatedMethodSource mtdSource, Set<Integer> paramIndexCallee) {
        if(callerHistory.contains(sootMethod) || depth >ConstantUtils.CALLDEPTH)  return;
        callerHistory.add(sootMethod);
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod edgeSource = edge.getSrc().method();
            Set<Integer> paramIndexCaller = new HashSet<>();
            if(mtdSource == RelatedMethodSource.CALLER){
                paramIndexCaller = getIndexesFromMethod(edge, paramIndexCallee);
                if(paramIndexCaller.size() ==0 ) continue;
            }

            List<SootClass> subClasses = SootUtils.getSubClasses(edgeSource);
//          subClasses.add(edgeSource.getDeclaringClass());
            for (SootClass sootClass : subClasses) {
                String signature = edgeSource.getSignature().replace(edgeSource.getDeclaringClass().getName(), sootClass.getName());
                if(SootUtils.getSootMethodBySignature(signature) == null || sootClass == edgeSource.getDeclaringClass()) {
                    String pkg1 = sootClass.getPackageName();
                    String pkg2 = exceptionInfo.getSootMethod().getDeclaringClass().getPackageName();
                    //filter a set of candidates!!!
                    if(!getPkgPrefix(pkg1, 2).equals(getPkgPrefix(pkg2,2))) continue;
                    if(edgeSource.isPublic()) {
                        RelatedMethod addMethod = new RelatedMethod(signature, mtdSource, depth);
                        if (edgeSource.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                            exceptionInfo.addRelatedMethodsInSameClass(addMethod);
                        else
                            exceptionInfo.addRelatedMethodsInDiffClass(addMethod);
                        exceptionInfo.addRelatedMethods(signature);
                    }
                    getExceptionCallerByParam(edgeSource, exceptionInfo, callerHistory, depth + 1, mtdSource, paramIndexCaller);
                }
            }

        }
    }

    public String getPkgPrefix(String pkg, int num) {
        String ss[] = pkg.split(".");
        if(ss.length < num) return  pkg;

        String prefix = "";
        for(int i=0; i<num;i++){
            prefix += ss[i]+".";
        }
        return  prefix;
    }

    private Set<Integer> getIndexesFromMethod(Edge edge, Set<Integer> paramIndexCallee) {
        SootMethod caller = edge.getSrc().method();
        SootMethod callee = edge.getTgt().method();
        Set<Integer> paramIndexCaller = new HashSet<>();
        for(Unit unit: caller.getActiveBody().getUnits()){
            InvokeExpr invoke = SootUtils.getInvokeExp(unit);
            if(invoke!=null && invoke.getMethod() == callee){
                for(int index: paramIndexCallee){
                    Value value = invoke.getArg(index);
                    getIndexesFromUnit(new ArrayList<>(),caller, unit, value, paramIndexCaller);
                }
            }
        }

        return paramIndexCaller;
    }

    private void getIndexesFromUnit(List<Value> valueHistory, SootMethod caller, Unit unit, Value value, Set<Integer> paramIndexCaller) {
        if(valueHistory.contains(value) ) return;  // if defUnit is not a pred of unit
        valueHistory.add(value);
        if(!(value instanceof  Local)) return;
        for(Unit defUnit: SootUtils.getDefOfLocal(caller.getSignature(),value, unit)) {
            if (defUnit instanceof JIdentityStmt) {
                JIdentityStmt identityStmt = (JIdentityStmt) defUnit;
                identityStmt.getRightOp();
                if (identityStmt.getRightOp() instanceof ParameterRef) {
                    //from parameter
                    paramIndexCaller.add(((ParameterRef) identityStmt.getRightOp()).getIndex());
                }
            } else if (defUnit instanceof JAssignStmt) {
                Value rightOp = ((JAssignStmt) defUnit).getRightOp();
                if (rightOp instanceof Local) {
                    getIndexesFromUnit( valueHistory, caller, defUnit, rightOp, paramIndexCaller);
                } else if (rightOp instanceof Expr) {
                    if (rightOp instanceof InvokeExpr) {
                        InvokeExpr invokeExpr = SootUtils.getInvokeExp(defUnit);
                        for (Value val : invokeExpr.getArgs())
                            getIndexesFromUnit( valueHistory, caller, defUnit, val, paramIndexCaller);
                        if (rightOp instanceof InstanceInvokeExpr) {
                            getIndexesFromUnit( valueHistory, caller, defUnit, ((InstanceInvokeExpr) rightOp).getBase(), paramIndexCaller);
                        }
                    } else if (rightOp instanceof AbstractInstanceOfExpr || rightOp instanceof AbstractCastExpr
                            || rightOp instanceof AbstractBinopExpr || rightOp instanceof AbstractUnopExpr) {
                        for (ValueBox vb : rightOp.getUseBoxes()) {
                            getIndexesFromUnit( valueHistory, caller, defUnit, vb.getValue(), paramIndexCaller);
                        }
                    } else if (rightOp instanceof NewExpr) {
                        List<UnitValueBoxPair> usesOfOps = SootUtils.getUseOfLocal(caller.getSignature(), defUnit);
                        for (UnitValueBoxPair use : usesOfOps) {
                            for (ValueBox vb : use.getUnit().getUseBoxes())
                                getIndexesFromUnit( valueHistory, caller, defUnit, vb.getValue(), paramIndexCaller);
                        }
                    }
                }else if (rightOp instanceof JArrayRef) {
                    JArrayRef jArrayRef = (JArrayRef) rightOp;
                    getIndexesFromUnit( valueHistory, caller, defUnit, jArrayRef.getBase(), paramIndexCaller);
                }else if (rightOp instanceof JInstanceFieldRef) {
                    JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) rightOp;
                    getIndexesFromUnit( valueHistory, caller, defUnit, jInstanceFieldRef.getBase(), paramIndexCaller);
                } else {
//                    rvalue = constant | local | expr | array_ref | instance_field_ref |
//                            next_next_stmt_address | static_field_ref;
//                    System.err.println(rightOp.getClass());
                }
            }
        }
    }



    private void getExceptionCallerByField(SootMethod sootMethod, ExceptionInfo exceptionInfo, HashSet<SootMethod> callerHistory, int depth,RelatedMethodSource mtdSource) {
//        if(callerHistory.contains(sootMethod) || depth >ConstantUtils.CALLDEPTH)
//            return;
//        callerHistory.add(sootMethod);
        for(SootField field: exceptionInfo.getRelatedFieldValues()){
            for(SootMethod otherMethod: sootMethod.getDeclaringClass().getMethods()){
                if(!otherMethod.hasActiveBody()) continue;
                if(fieldIsChanged(field, otherMethod)){
                    if(otherMethod.isPublic()) {
                        RelatedMethod addMethod = new RelatedMethod(otherMethod.getSignature(),mtdSource,depth);
                        if(otherMethod.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                            exceptionInfo.addRelatedMethodsInSameClass(addMethod);
                        else
                            exceptionInfo.addRelatedMethodsInDiffClass(addMethod);
                        exceptionInfo.addRelatedMethods(otherMethod.getSignature());
                    }
                    getExceptionCallerByParam(otherMethod, exceptionInfo, callerHistory, depth+1, RelatedMethodSource.FIELDCALLER, new HashSet<>());
                }
            }
        }
    }

    public static boolean fieldIsChanged(SootField field, SootMethod sootMethod) {
        for(Unit u: sootMethod.getActiveBody().getUnits()){
            if(u instanceof  JAssignStmt){
                JAssignStmt jAssignStmt = (JAssignStmt) u;
                if(jAssignStmt.getLeftOp() instanceof  FieldRef){
                    if (field ==  jAssignStmt.getFieldRef().getField()) {
                        return true;
                    }
                }else if(jAssignStmt.getRightOp() instanceof  FieldRef){
                    if (field ==  jAssignStmt.getFieldRef().getField()) {
                        List<UnitValueBoxPair> uses = SootUtils.getUseOfLocal(sootMethod.getSignature(), jAssignStmt);
                        for(UnitValueBoxPair pair:uses){
                            if(pair.getUnit() instanceof JAssignStmt){
                                JAssignStmt jAssignStmt2 = (JAssignStmt) pair.getUnit();
                                if(jAssignStmt2.getRightOp() == pair.getValueBox().getValue()){
                                    return  false;
                                }
                                return true;
                            }else if(pair.getUnit() instanceof JInvokeStmt){
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    /**
     * get the latest condition info for an ExceptionInfo
     * only analyze one level if condition, forward
     */
    private void getExceptionCondition(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, Set<Unit> getCondHistory) {
        if(getCondHistory.contains(unit) || getCondHistory.size()> ConstantUtils.CONDITIONHISTORYSIZE) return;// if defUnit is not a pred of unit
        getCondHistory.add(unit);
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        List<Unit> allPreds = new ArrayList<>();
        SootUtils.getAllPredsofUnit(sootMethod, unit,allPreds);
        List<Unit> gotoTargets = getGotoTargets(body);
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if(exceptionInfo.getTracedUnits().size()>0 && gotoTargets.contains(predUnit))
                break;
            if (predUnit instanceof IfStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                IfStmt ifStmt = (IfStmt) predUnit;
                Value cond = ifStmt.getCondition();
                exceptionInfo.addRelatedCondition(cond);
                if(cond instanceof ConditionExpr){
                    Value value = ((ConditionExpr)cond).getOp1();
                    extendRelatedValues(allPreds, exceptionInfo, predUnit, value, new ArrayList<>(),getCondHistory);
                }
            }else if (predUnit instanceof SwitchStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                SwitchStmt swStmt = (SwitchStmt) predUnit;
                Value key = swStmt.getKey();
                extendRelatedValues(allPreds, exceptionInfo, predUnit, key, new ArrayList<>(), getCondHistory);
            }else if (predUnit instanceof JIdentityStmt ) {
                JIdentityStmt stmt = (JIdentityStmt) predUnit;
                if(stmt.getRightOp() instanceof CaughtExceptionRef){
                    exceptionInfo.addCaughtedValues(stmt.getRightOp());
                }
            }
            getExceptionCondition(sootMethod, predUnit, exceptionInfo,getCondHistory);
        }
    }

    /**
     * tracing the values relates to the one used in if condition
     */
    private void extendRelatedValues(List<Unit> allPreds, ExceptionInfo exceptionInfo, Unit unit, Value value,
                                     List<Value> valueHistory, Set<Unit> getCondHistory) {
        if(valueHistory.contains(value) || !allPreds.contains(unit)) return;// if defUnit is not a pred of unit
        valueHistory.add(value);
        if(value instanceof  Local) {
            String methodSig = exceptionInfo.getSootMethod().getSignature();
            for(Unit defUnit: SootUtils.getDefOfLocal(methodSig,value, unit)) {
                //if the define unit is under a check
                if (defUnit instanceof JIdentityStmt) {
                    JIdentityStmt identityStmt = (JIdentityStmt) defUnit;
                    identityStmt.getRightOp();
                    if (identityStmt.getRightOp() instanceof ParameterRef) {
                        //from parameter
                        exceptionInfo.addRelatedParamValue(identityStmt.getRightOp());
                        if(identityStmt.getRightOp() instanceof  ParameterRef)
                            exceptionInfo.getRelatedValueIndex().add(((ParameterRef) identityStmt.getRightOp()).getIndex());
                    }else if(identityStmt.getRightOp() instanceof CaughtExceptionRef){
                        exceptionInfo.addCaughtedValues(identityStmt.getRightOp());
                    }
                } else if (defUnit instanceof JAssignStmt) {
                    Value rightOp = ((JAssignStmt) defUnit).getRightOp();
                    if (rightOp instanceof Local) {
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, rightOp, valueHistory, getCondHistory);
                    } else if (rightOp instanceof AbstractInstanceFieldRef) {
                        SootField f = ((AbstractInstanceFieldRef) rightOp).getField();
                        exceptionInfo.addRelatedFieldValues(f);
                    } else if (rightOp instanceof Expr) {
                        if (rightOp instanceof InvokeExpr) {
                            InvokeExpr invokeExpr = SootUtils.getInvokeExp(defUnit);
                            for (Value val : invokeExpr.getArgs())
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, val, valueHistory, getCondHistory);
                            if (rightOp instanceof InstanceInvokeExpr) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, ((InstanceInvokeExpr) rightOp).getBase(), valueHistory, getCondHistory);
                            }
                        } else if (rightOp instanceof AbstractInstanceOfExpr || rightOp instanceof AbstractCastExpr
                                || rightOp instanceof AbstractBinopExpr || rightOp instanceof AbstractUnopExpr) {
                            for (ValueBox vb : rightOp.getUseBoxes()) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, vb.getValue(), valueHistory, getCondHistory);
                            }
                        } else if (rightOp instanceof NewExpr) {
                            List<UnitValueBoxPair> usesOfOps = SootUtils.getUseOfLocal(exceptionInfo.getSootMethod().getSignature(), defUnit);
                            for (UnitValueBoxPair use : usesOfOps) {
                                for (ValueBox vb : use.getUnit().getUseBoxes())
                                    extendRelatedValues(allPreds, exceptionInfo, use.getUnit(), vb.getValue(), valueHistory, getCondHistory);
                            }
                        } else {
                            getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory);
                        }
                    } else if (rightOp instanceof StaticFieldRef) {
                        //from static field value
                        exceptionInfo.addRelatedFieldValues(((StaticFieldRef) rightOp).getField());
                        return;
                    }else if (rightOp instanceof JArrayRef) {
                        JArrayRef jArrayRef = (JArrayRef) rightOp;
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, jArrayRef.getBase(), valueHistory, getCondHistory);
                    }else if (rightOp instanceof JInstanceFieldRef) {
                        JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) rightOp;
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, jInstanceFieldRef.getBase(), valueHistory, getCondHistory);
                    }else {
                        getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory);
                    }
                } else {
                    System.out.println(defUnit.getClass().getName() + "::" + defUnit);
                }
            }
        }
    }

    /**
     * get the goto destination of IfStatement
     */
    private List<Unit> getGotoTargets(Body body) {
        List<Unit> res = new ArrayList<>();
        for(Unit u : body.getUnits()){
            if(u instanceof JIfStmt){
                JIfStmt ifStmt = (JIfStmt)u;
                res.add(ifStmt.getTargetBox().getUnit());
            }
            else if(u instanceof GotoStmt){
                GotoStmt gotoStmt = (GotoStmt)u;
                res.add(gotoStmt.getTargetBox().getUnit());
            }
        }
        return res;
    }

    /**
     * get the msg info for an ExceptionInfo
     */
    private void getExceptionMessage(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, List<Integer> times){
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        String exceptionClassName = exceptionInfo.getExceptionType();
        times.add(1);
        if (times.size() > 50) {
            return;
        }
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if (predUnit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) predUnit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                if (invokeExpr.getMethod().getDeclaringClass().toString().equals(exceptionClassName)) {
                    // 可能初始化会有多个参数，只关注第一个String参数
                    if (invokeExpr.getArgCount() > 0 && StringUtils.isStringType(invokeExpr.getArgs().get(0).getType())) {
                        Value arg = invokeExpr.getArgs().get(0);
                        if (arg instanceof Local) {
                            List<String> message = Lists.newArrayList();
                            message.add("");
                            getMsgContentByTracingValue(sootMethod, (Local) arg, unit, message);
                            exceptionInfo.setExceptionMsg(message.get(0));
                        } else if (arg instanceof Constant) {
                            StringConstant arg1 = (StringConstant) arg;
                            exceptionInfo.setExceptionMsg(arg1.value);
                        }
                    }
                } else {
                    getExceptionMessage(sootMethod, predUnit, exceptionInfo,times);
                }
            } else {
                getExceptionMessage(sootMethod, predUnit, exceptionInfo,times);
            }
        }
    }


    /**
     * getMsgContentByTracingValue
     */
    private void getMsgContentByTracingValue(SootMethod sootMethod, Local localTemp, Unit unit, List<String> message){
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
        Unit defOfLocal = defsOfOps.get(0);
        if (defOfLocal.equals(unit)) {
            return;
        }
        if (defOfLocal instanceof DefinitionStmt) {
            Value rightOp = ((DefinitionStmt) defOfLocal).getRightOp();
            if (rightOp instanceof Constant) {
                String s = message.get(0) + rightOp;
                message.set(0,s);
            } else if (rightOp instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) rightOp;
                String invokeSig = invokeExpr.getMethod().getSignature();
                if (invokeSig.equals("<java.lang.StringBuilder: java.lang.String toString()>")) {
                    Value value = invokeExpr.getUseBoxes().get(0).getValue();
                    if (value instanceof Local) {
                        getMsgContentByTracingValue(sootMethod, (Local) value, unit, message);
                    }
                } else if (invokeSig.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder append")) {
                    Value argConstant = invokeExpr.getArgs().get(0);
                    String s;
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = value + message.get(0);
                        } else {
                            s = argConstant + message.get(0);
                        }

                    } else {
                        s = "[\\s\\S]*" + message.get(0);
                    }
                    message.set(0, s);

                    Value value = ((JVirtualInvokeExpr) invokeExpr).getBaseBox().getValue();
                    if (value instanceof Local) {
                        getMsgContentByTracingValue(sootMethod, (Local) value, unit, message);
                    }
                }
            } else if (rightOp instanceof NewExpr) {
                NewExpr rightOp1 = (NewExpr) rightOp;
                if (rightOp1.getBaseType().toString().equals("java.lang.StringBuilder")) {
                    traceStringBuilderBack(sootMethod, defOfLocal, message, 0);
                }
            } else if (rightOp instanceof Local) {
                getMsgContentByTracingValue(sootMethod, (Local) rightOp, unit, message ) ;
            }
        }
    }

    /**
     * traceStringBuilderBack
     */
    private void traceStringBuilderBack(SootMethod sootMethod, Unit unit, List<String> message, int index){
        if (index > 10) {
            return;
        }
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        List<Unit> succsOf = unitGraph.getSuccsOf(unit);
        for (Unit succs : succsOf) {
            if (succs instanceof InvokeStmt) {
                InvokeExpr invokeExpr = ((InvokeStmt) succs).getInvokeExpr();
                String invokeSig = invokeExpr.getMethod().getSignature();
                if (invokeSig.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder append")) {
                    Value argConstant = invokeExpr.getArgs().get(0);
                    String s;
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = message.get(0) + value;
                        } else {
                            s = message.get(0) + argConstant;
                        }
                    } else{
                        s = message.get(0) + "[\\s\\S]*";
                    }
                    message.set(0, s);
                }
            } else if (succs instanceof ThrowStmt) {
                return;
            }
            traceStringBuilderBack(sootMethod, succs, message, index + 1);
        }
    }


    /**
     * write to Json File after each class is Analyzed
     * @param sootClass
     */
    private void writeJsonForCurrentClass(SootClass sootClass) {
        String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
                + File.separator + "exceptionInfo" +File.separator;
        FileUtils.createFolder(summary_app_dir);
        if(exceptionInfoList.size()>0)
            ExceptionInfoClientOutput.writeToJson(summary_app_dir+sootClass.getName()+".json", exceptionInfoList);
    }

}
