package com.xxl.job.admin.core.alarm.impl;

import com.xxl.job.admin.core.alarm.JobAlarm;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.model.ReturnT;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * @author jiangsai
 * @create 2020-09-25 11:34
 */
@Component
@Slf4j
public class FeiShuJobAlarm implements JobAlarm {

    @Autowired
    private RestTemplate restTemplate;

    private String feiShuMsg = "{\n" +
            "    \"msg_type\": \"post\",\n" +
            "    \"content\": {\n" +
            "        \"post\": {\n" +
            "            \"zh_cn\": {\n" +
            "                \"title\": \"XXL-JOB报警\",\n" +
            "                \"content\": [\n" +
            "                    [\n" +
            "                        {\n" +
            "                            \"tag\": \"text\",\n" +
            "                            \"text\": \"{content}\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"tag\": \"a\",\n" +
            "                            \"text\": \"详情\",\n" +
            "                            \"href\": \"{xxljobaddress}\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "} ";

    @Override
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog) {
        boolean alarmResult = true;
        if (info==null || StringUtils.isBlank(info.getFeiShuUrl())) {
            return alarmResult;
        }
        // alarmContent
        String alarmContent = "Alarm Job LogId=" + jobLog.getId();
        if (StringUtils.isNotBlank(jobLog.getTriggerMsg())) {
            alarmContent += " TriggerMsg= " + jobLog.getTriggerMsg();
        }
        if (jobLog.getHandleCode()>0 && jobLog.getHandleCode() != ReturnT.SUCCESS_CODE) {
            alarmContent += " HandleCode=" + jobLog.getHandleMsg();
        }


        try {
            String feiShuUrl = info.getFeiShuUrl();
            if(StringUtils.isNotBlank(alarmContent)){
                alarmContent = alarmContent.replaceAll("\\n", "")
                        .replaceAll("\\t", "")
                        .replaceAll("\\r", "")
                        .replaceAll("\"", "'");
                if( alarmContent.length() > 500){
                    alarmContent = alarmContent.substring(0, 500);
                }
            }

            if(StringUtils.isNotBlank(alarmContent)){
                alarmContent.replaceAll("<br>", " ");
            }

            XxlJobGroup group = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().load(Integer.valueOf(info.getJobGroup()));
            String groupName = group!=null?group.getTitle():"null";
            String alertType = " 告警类型:"+I18nUtil.getString("jobconf_monitor_alarm_type");
            feiShuMsg = feiShuMsg.replace("{content}", "执行器:"+groupName+" 任务id:"+info.getId()+" 任务描述:"+info.getJobDesc()+alertType+" 告警内容:"+alarmContent);
            feiShuMsg = feiShuMsg.replace("{xxljobaddress}",XxlJobAdminConfig.getAdminConfig().getXxlJobUrl()+"/joblog?jobId="+info.getId());
            String result = restTemplate.postForObject(feiShuUrl, feiShuMsg, String.class);
            log.info(result);
        } catch (Exception e) {
            log.error(">>>>>>>>>>> xxl-job, job fail alarm feishu send error, JobLogId:{}", jobLog.getId(), e);

            alarmResult = false;
        }
        return alarmResult;
    }


}
