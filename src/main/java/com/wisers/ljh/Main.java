package com.wisers.ljh;

import com.wisers.ljh.entity.BusyThreadInfo;
import com.wisers.ljh.entity.ThreadSampler;
import com.wisers.ljh.entity.ThreadVO;
import com.wisers.ljh.utils.ArrayUtils;
import com.wisers.ljh.utils.ThreadUtil;
import net.bytebuddy.agent.VirtualMachine;
import sun.management.ConnectorAddressLink;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static ThreadMXBean threadMXBean = null;

    /**
     * 参数三个  pid 采集数量 采集时长/ms
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            throw new RuntimeException("please check params");
        }
        String url = ConnectorAddressLink.importFrom(Integer.parseInt(args[0]));
        if (url == null || url.trim().equals("")) {
            VirtualMachine virtualMachine = VirtualMachine.ForHotSpot.attach(args[0]);
            url = virtualMachine.startLocalManagementAgent();
        }
        JMXServiceURL jmxServiceURL = new JMXServiceURL(url);
        JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxServiceURL);
        MBeanServerConnection server = jmxConnector.getMBeanServerConnection();
        threadMXBean = ManagementFactory.newPlatformMXBeanProxy(server, ManagementFactory.THREAD_MXBEAN_NAME,
                ThreadMXBean.class);
        processTopBusyThreads(Integer.parseInt(args[1]), Long.parseLong(args[2]));
    }

    private static void processTopBusyThreads(int num, long sampleInterval) {
        ThreadSampler threadSampler = new ThreadSampler();
        threadSampler.setThreadMXBean(threadMXBean);
        threadSampler.sample(ThreadUtil.getThreads());
        threadSampler.pause(sampleInterval);
        List<ThreadVO> threadStats = threadSampler.sample(ThreadUtil.getThreads());

        int limit = Math.min(threadStats.size(), num);

        List<ThreadVO> topNThreads = null;
        if (limit > 0) {
            topNThreads = threadStats.subList(0, limit);
        } else { // -1 for all threads
            topNThreads = threadStats;
        }

        List<Long> tids = new ArrayList<Long>(topNThreads.size());
        for (ThreadVO thread : topNThreads) {
            if (thread.getId() > 0) {
                tids.add(thread.getId());
            }
        }

        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(ArrayUtils.toPrimitive(tids.toArray(new Long[0])), false,
                false);
        if (tids.size()> 0 && threadInfos == null) {
            return;
        }

        //threadInfo with cpuUsage
        List<BusyThreadInfo> busyThreadInfos = new ArrayList<BusyThreadInfo>(topNThreads.size());
        for (ThreadVO thread : topNThreads) {
            ThreadInfo threadInfo = findThreadInfoById(threadInfos, thread.getId());
            if (threadInfo != null) {
                BusyThreadInfo busyThread = new BusyThreadInfo(thread, threadInfo);
                busyThreadInfos.add(busyThread);
            }
        }
        for (BusyThreadInfo info : busyThreadInfos) {
            String stacktrace = ThreadUtil.getFullStacktrace(info, -1, -1);
            System.out.println(stacktrace);
        }
    }

    private static ThreadInfo findThreadInfoById(ThreadInfo[] threadInfos, long id) {
        for (int i = 0; i < threadInfos.length; i++) {
            ThreadInfo threadInfo = threadInfos[i];
            if (threadInfo != null && threadInfo.getThreadId() == id) {
                return threadInfo;
            }
        }
        return null;
    }
}
