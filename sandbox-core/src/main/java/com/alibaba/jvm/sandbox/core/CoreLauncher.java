package com.alibaba.jvm.sandbox.core;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static com.alibaba.jvm.sandbox.core.util.SandboxStringUtils.getCauseMessage;

/**
 * 沙箱内核启动器
 * Created by luanjia@taobao.com on 16/10/2.
 */
public class CoreLauncher {

    public CoreLauncher(final String targetJvmPid,
                        final String agentJarPath,
                        final String token) throws Exception {

        // 加载agent
        attachAgent(targetJvmPid, agentJarPath, token);

    }

    /**
     * 内核启动程序
     *
     * @param args 参数
     *             [0] : PID
     *             [1] : agent.jar's value
     *             [2] : token
     */
    public static void main(String[] args) {
        try {

            // check args
            if (args.length != 3
                    || StringUtils.isBlank(args[0])
                    || StringUtils.isBlank(args[1])
                    || StringUtils.isBlank(args[2])) {
                throw new IllegalArgumentException("illegal args");
            }

            // call the core launcher
            new CoreLauncher(args[0], args[1], args[2]);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.err.println("sandbox load jvm failed : " + getCauseMessage(t));
            System.exit(-1);
        }
    }

    // 加载Agent
    private void attachAgent(final String targetJvmPid,
                             final String agentJarPath,
                             final String cfg) throws Exception {

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Class<?> vmdClass = loader.loadClass("com.sun.tools.attach.VirtualMachineDescriptor");
        final Class<?> vmClass = loader.loadClass("com.sun.tools.attach.VirtualMachine");

        //获取jvm实例列表
        Object attachVmdObj = null;
        for (Object obj : (List<?>) vmClass.getMethod("list", (Class<?>[]) null).invoke(null, (Object[]) null)) {
            //根据传入待绑定jvm的进程id匹配目标jvm实例
            if ((vmdClass.getMethod("id", (Class<?>[]) null).invoke(obj, (Object[]) null))
                    .equals(targetJvmPid)) {
                //获取待匹配目标jvm实例
                attachVmdObj = obj;
            }
        }

        Object vmObj = null;
        try {
            // 使用 attach(String pid) 这种方式
            if (null == attachVmdObj) {
                //如果上方代码无法获取到jvm实例，通过attach(String pid)的形式再次重试，ps:此处感觉多余。
                vmObj = vmClass.getMethod("attach", String.class).invoke(null, targetJvmPid);
            } else {
                //获得待attach的jvm实例后，通过jvmti接口连接。
                vmObj = vmClass.getMethod("attach", vmdClass).invoke(null, attachVmdObj);
            }
            //将agent代理加载到目标jvm实例中，此处vmObj可能为null，从而导致异常
            vmClass
                    .getMethod("loadAgent", String.class, String.class)
                    .invoke(vmObj, agentJarPath, cfg);
        } finally {
            if (null != vmObj) {
                vmClass.getMethod("detach", (Class<?>[]) null).invoke(vmObj, (Object[]) null);
            }
        }

    }

}
