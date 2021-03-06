package com.xxl.job.executor.service.jobhandler;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.ShardingUtil;
import com.xxl.job.core.util.XxlJobThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * XxlJob开发示例（Bean模式）
 *
 * 开发步骤：
 * 1、在Spring Bean实例中，开发Job方法，方式格式要求为 "public ReturnT<String> execute(String param)"
 * 2、为Job方法添加注解 "@XxlJob(value="自定义jobhandler名称", init = "JobHandler初始化方法", destroy = "JobHandler销毁方法")"，注解value值对应的是调度中心新建任务的JobHandler属性的值。
 * 3、执行日志：需要通过 "XxlJobLogger.log" 打印执行日志；
 *
 * @author xuxueli 2019-12-11 21:52:51
 */
@Component
public class SampleXxlJob {
    private static Logger logger = LoggerFactory.getLogger(SampleXxlJob.class);


    /**
     * 1、简单任务示例（Bean模式）
     */
    @XxlJob("demoJobHandler")
    public ReturnT<String> demoJobHandler(String param) throws Exception {
        XxlJobLogger.log("XXL-JOB, Hello World.");

        for (int i = 0; i < 5000000; i++) {
            //使用interrupt打断就得在循环里判断标识
            if(Thread.currentThread().isInterrupted()){
               break;
            }

            XxlJobLogger.log("beat at:" + i);
            System.out.println("hello");

        }
        System.out.println("hello!!!!");
        return ReturnT.SUCCESS;
    }



    /**
     * 2、分片广播任务
     */
    @XxlJob("shardingJobHandler")
    public ReturnT<String> shardingJobHandler(String param) throws Exception {

        //获取执行总数在redis取吧.

        // 分片参数
        ShardingUtil.ShardingVO shardingVO = ShardingUtil.getShardingVo();
        XxlJobLogger.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardingVO.getIndex(), shardingVO.getTotal());

        // 业务逻辑
        for (int i = 0; i < shardingVO.getTotal(); i++) {
            if (i == shardingVO.getIndex()) {
                XxlJobLogger.log("第 {} 片, 命中分片开始处理", i);
            } else {
                XxlJobLogger.log("第 {} 片, 忽略", i);
            }
        }

        return ReturnT.SUCCESS;
    }


    /**
     * 3、命令行任务
     */
    @XxlJob("commandJobHandler")
    public ReturnT<String> commandJobHandler(String param) throws Exception {
        String command = param;
        int exitValue = -1;

        BufferedReader bufferedReader = null;
        try {
            // command process
            Process process = Runtime.getRuntime().exec(command);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream));

            // command log
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                XxlJobLogger.log(line);
            }

            // command exit
            process.waitFor();
            exitValue = process.exitValue();
        } catch (Exception e) {
            XxlJobLogger.log(e);
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }

        if (exitValue == 0) {
            return IJobHandler.SUCCESS;
        } else {
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "command exit value("+exitValue+") is failed");
        }
    }


    /**
     * 4、跨平台Http任务
     *  参数示例：
     *      "url: http://www.baidu.com\n" +
     *      "method: get\n" +
     *      "data: content\n";
     */
    @XxlJob("httpJobHandler")
    public ReturnT<String> httpJobHandler(String param) throws Exception {

        // param parse
        if (param==null || param.trim().length()==0) {
            XxlJobLogger.log("param["+ param +"] invalid.");
            return ReturnT.FAIL;
        }
        String[] httpParams = param.split("\n");
        String url = null;
        String method = null;
        String data = null;
        for (String httpParam: httpParams) {
            if (httpParam.startsWith("url:")) {
                url = httpParam.substring(httpParam.indexOf("url:") + 4).trim();
            }
            if (httpParam.startsWith("method:")) {
                method = httpParam.substring(httpParam.indexOf("method:") + 7).trim().toUpperCase();
            }
            if (httpParam.startsWith("data:")) {
                data = httpParam.substring(httpParam.indexOf("data:") + 5).trim();
            }
        }

        // param valid
        if (url==null || url.trim().length()==0) {
            XxlJobLogger.log("url["+ url +"] invalid.");
            return ReturnT.FAIL;
        }
        if (method==null || !Arrays.asList("GET", "POST").contains(method)) {
            XxlJobLogger.log("method["+ method +"] invalid.");
            return ReturnT.FAIL;
        }

        // request
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        try {
            // connection
            URL realUrl = new URL(url);
            connection = (HttpURLConnection) realUrl.openConnection();

            // connection setting
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(5 * 1000);
            connection.setConnectTimeout(3 * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");

            // do connection
            connection.connect();

            // data
            if (data!=null && data.trim().length()>0) {
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.write(data.getBytes("UTF-8"));
                dataOutputStream.flush();
                dataOutputStream.close();
            }

            // valid StatusCode
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new RuntimeException("Http Request StatusCode(" + statusCode + ") Invalid.");
            }

            // result
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            String responseMsg = result.toString();

            XxlJobLogger.log(responseMsg);
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            XxlJobLogger.log(e);
            return ReturnT.FAIL;
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                XxlJobLogger.log(e2);
            }
        }

    }


    private volatile static ThreadPoolExecutor pool = null;
    /**
     * 5、生命周期任务示例：任务初始化与销毁时，支持自定义相关逻辑；
     * 线程池示例
     */
    @XxlJob(value = "demoJobHandler2", init = "init", destroy = "destroy")
    public ReturnT<String> demoJobHandler2(String param) throws Exception {
        XxlJobLogger.log("XXL-JOB, Hello World.");
        for(int i=0; i<8;i++){
            pool.submit(() -> {
                for(int j=0; j<10000000; j++){
                    if(Thread.currentThread().isInterrupted()){
                        break;
                    }
                    XxlJobLogger.log("beat at:" + j);
                    System.out.println("hello");
                }
            });

        }
        boolean done = false;
        do{
            //使用interrupt打断就得在循环里判断标识
            if(Thread.currentThread().isInterrupted()){
                break;
            }
            done = pool.getTaskCount() == pool.getCompletedTaskCount();
        }while (!done);
        System.out.println("任务结束");
        return ReturnT.SUCCESS;
    }

    /**
     * init方法会在程序运行前被调用
     */
    public void init(){
        if(pool == null || pool.isTerminated()){
            pool = new ThreadPoolExecutor(5, 10, 5000,
                    TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                    Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
        }
        logger.info("init");
    }

    /**
     * 但destroy并不会在主程序运行完被调用,destroy只能在手工停止时候调用
     */
    public void destroy(){
        //线程池并没有提供粗暴关闭的方法,只能在线程池任务判断interrupt状态
        //如果一定要强制关闭,请使用本项目里的XxlJobThreadPoolExecutor.shutdownFore();
        pool.shutdownNow();
        pool = null;
        logger.info("destory");
    }

    private volatile static XxlJobThreadPoolExecutor xxlJobPool = null;

    /**
     * 6、生命周期任务示例：任务初始化与销毁时，支持自定义相关逻辑；
     * 线程池示例
     * 使用本项目里的XxlJobThreadPoolExecutor强制关闭线程池
     */
    @XxlJob(value = "demoJobHandlerShutdownForce", init = "initShutdownForce", destroy = "destroyShutdownForce")
    public ReturnT<String> demoJobHandlerShutdownForce(String param) throws Exception {
        XxlJobLogger.log("XXL-JOB, Hello World.");
        for(int i=0; i<8;i++){
            xxlJobPool.submit(() -> {
                for(int j=0; j<10000000; j++){
                    XxlJobLogger.log("beat at:" + j);
                    System.out.println("hello");
                }
            });

        }
        boolean done = false;
        do{
            done = xxlJobPool.getTaskCount() == xxlJobPool.getCompletedTaskCount();
        }while (!done);
        System.out.println("任务结束");
        return ReturnT.SUCCESS;
    }

    /**
     * init方法会在程序运行前被调用
     */
    public void initShutdownForce(){
        if(xxlJobPool == null || xxlJobPool.isTerminated()){
            xxlJobPool = new XxlJobThreadPoolExecutor(5, 10, 5000,
                    TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                    Executors.defaultThreadFactory(), new XxlJobThreadPoolExecutor.CallerRunsPolicy());
        }
        logger.info("init");
    }

    /**
     * 但destroy并不会在主程序运行完被调用,destroy只能在手工停止时候调用
     */
    public void destroyShutdownForce(){
        //线程池并没有提供粗暴关闭的方法,只能在线程池任务判断interrupt状态
        //如果一定要强制关闭,请使用本项目里的XxlJobThreadPoolExecutor.shutdownFore();
        xxlJobPool.shutdownForce();
        xxlJobPool = null;
        logger.info("destory");
    }


}
