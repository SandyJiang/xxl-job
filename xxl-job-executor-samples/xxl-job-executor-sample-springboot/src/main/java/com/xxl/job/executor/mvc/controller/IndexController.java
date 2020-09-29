package com.xxl.job.executor.mvc.controller;

import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

//package com.xxl.job.executor.mvc.controller;
//
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//@Controller
//@EnableAutoConfiguration
//public class IndexController {
//
//    @RequestMapping("/")
//    @ResponseBody
//    String index() {
//        return "xxl job executor running.";
//    }
//
//}
@RestController
public class IndexController {

    @RequestMapping("/")
    @ResponseBody
    String index() {
       for(int i=0; i<50; i++){
           XxlJobLogger.log("helelo"+i);
       }
       return "xxl job executor running.";
    }

}