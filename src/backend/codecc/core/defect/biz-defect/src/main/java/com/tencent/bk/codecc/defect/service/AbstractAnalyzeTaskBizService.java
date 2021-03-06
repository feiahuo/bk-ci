/*
 * Tencent is pleased to support the open source community by making BK-CODECC 蓝鲸代码检查平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CODECC 蓝鲸代码检查平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.codecc.defect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.tencent.bk.codecc.defect.component.ScmJsonComponent;
import com.tencent.bk.codecc.defect.dao.mongorepository.*;
import com.tencent.bk.codecc.defect.dao.mongotemplate.BuildDao;
import com.tencent.bk.codecc.defect.dao.mongotemplate.ToolBuildInfoDao;
import com.tencent.bk.codecc.defect.dao.redis.TaskAnalysisDao;
import com.tencent.bk.codecc.defect.dto.WebsocketDTO;
import com.tencent.bk.codecc.defect.model.*;
import com.tencent.bk.codecc.defect.model.incremental.ToolBuildStackEntity;
import com.tencent.bk.codecc.defect.service.file.ScmFileInfoService;
import com.tencent.bk.codecc.defect.utils.ThirdPartySystemCaller;
import com.tencent.bk.codecc.defect.vo.CommitDefectVO;
import com.tencent.bk.codecc.defect.vo.TaskLogVO;
import com.tencent.bk.codecc.defect.vo.UploadTaskLogStepVO;
import com.tencent.bk.codecc.defect.vo.customtool.ScmInfoVO;
import com.tencent.bk.codecc.task.api.ServiceToolRestResource;
import com.tencent.bk.codecc.task.vo.*;
import com.tencent.devops.common.api.ToolMetaBaseVO;
import com.tencent.devops.common.api.analysisresult.BaseLastAnalysisResultVO;
import com.tencent.devops.common.api.analysisresult.ToolLastAnalysisResultVO;
import com.tencent.devops.common.api.pojo.CodeCCResult;
import com.tencent.devops.common.client.Client;
import com.tencent.devops.common.constant.ComConstants;
import com.tencent.devops.common.constant.CommonMessageCode;
import com.tencent.devops.common.constant.RedisKeyConstants;
import com.tencent.devops.common.service.BizServiceFactory;
import com.tencent.devops.common.service.IBizService;
import com.tencent.devops.common.service.ToolMetaCacheService;
import com.tencent.devops.common.util.*;
import com.tencent.devops.common.web.mq.ConstantsKt;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务分析记录服务层
 *
 * @version V1.0
 * @date 2019/5/5
 */
@Slf4j
@ToString
public abstract class AbstractAnalyzeTaskBizService implements IBizService<UploadTaskLogStepVO>
{
    @Autowired
    public TaskAnalysisDao taskAnalysisDao;
    @Autowired
    protected TaskLogRepository taskLogRepository;
    @Autowired
    private BuildDao buildDao;
    @Autowired
    private ThirdPartySystemCaller thirdPartySystemCaller;
    @Autowired
    private Client client;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected TaskLogService taskLogService;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    public CodeRepoFromAnalyzeLogRepository codeRepoFromAnalyzeLogRepository;

    @Autowired
    private FirstAnalysisSuccessTimeRepository firstSuccessTimeRepository;

    @Autowired
    private DefectRepository defectRepository;

    @Autowired
    private BuildDefectRepository buildDefectRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    protected ToolMetaCacheService toolMetaCacheService;

    @Autowired
    public ToolBuildInfoDao toolBuildInfoDao;

    @Autowired
    private ToolBuildInfoRepository toolBuildInfoRepository;
    @Autowired
    private ToolBuildStackRepository toolBuildStackRepository;
    @Autowired
    public ScmJsonComponent scmJsonComponent;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ScmFileInfoService scmFileInfoService;


    @Autowired
    private BizServiceFactory<ISubsequentProcBizService> bizServiceFactory;

    @Autowired
    protected BizServiceFactory<IMessageQueueBizService> MessageBizServiceFactory;

    @Override
    public CodeCCResult processBiz(UploadTaskLogStepVO uploadTaskLogStepVO)
    {
        
        log.info("begin to upload tasklog: {}", uploadTaskLogStepVO);
        if (uploadTaskLogStepVO.getStartTime() != ComConstants.COMMON_NUM_0L)
        {
            uploadTaskLogStepVO.setStartTime(System.currentTimeMillis());
        }
        if (uploadTaskLogStepVO.getEndTime() != ComConstants.COMMON_NUM_0L)
        {
            uploadTaskLogStepVO.setEndTime(System.currentTimeMillis());
        }

        String streamName = uploadTaskLogStepVO.getStreamName();
        String toolName = uploadTaskLogStepVO.getToolName();
        String buildId = uploadTaskLogStepVO.getPipelineBuildId();

        // 调用task模块的接口获取任务信息
        TaskDetailVO taskVO = thirdPartySystemCaller.getTaskInfo(streamName);
        long taskId = taskVO.getTaskId();
        uploadTaskLogStepVO.setTaskId(taskId);

        TaskLogEntity lastTaskLogEntity = taskLogRepository.findByTaskIdAndToolNameAndBuildId(taskId, toolName, buildId);

        //创建analyzeSubsequentProcService处理器，区分工蜂项目
        ISubsequentProcBizService ISubsequentProcBizService = bizServiceFactory.createBizService(taskId,ComConstants.BusinessType.SUBSEQUENT_PROCESS.value(), ISubsequentProcBizService.class);

        //如果是流水线运行失败
        if(null != uploadTaskLogStepVO.getPipelineFail() && uploadTaskLogStepVO.getPipelineFail())
        {
            if(null != lastTaskLogEntity)
            {
                updateAbortAnalysisTaskLog(lastTaskLogEntity, toolName, "流水线运行异常");
            }
            else
            {
                //更新工具信息 用于后续websocket推送信息使用
                ToolConfigBaseVO toolConfigBaseVO = new ToolConfigBaseVO();
                createNewTaskLog(uploadTaskLogStepVO, taskVO, toolConfigBaseVO);
            }
        }

        // 不存在当前构建的任务记录，需需要新建
        if (lastTaskLogEntity == null)
        {
            log.info("begin to create new task log");

            // 如果第一步开始，需要创建版本号
            String analysisVersion = taskAnalysisDao.generateAnalysisVersion(taskId, toolName);

            //更新工具信息 用于后续websocket推送信息使用
            ToolConfigBaseVO toolConfigBaseVO = new ToolConfigBaseVO();
            TaskLogEntity taskLogEntity = createNewTaskLog(uploadTaskLogStepVO, taskVO, toolConfigBaseVO);

            // 分析开始前告警数据及告警统计数据预处理
            preHandleDefectsAndStatistic(uploadTaskLogStepVO, analysisVersion);

            //发送websocket信息
            ISubsequentProcBizService.analyzeSendWebSocketMsg(toolConfigBaseVO, uploadTaskLogStepVO, taskLogEntity, taskVO, taskId, toolName);

        }
        //否则，将上传步骤信息追加到该任务
        else
        {
            log.info("begin to update task log");
            ToolConfigBaseVO toolConfigBaseVO = updateTaskLog(lastTaskLogEntity, uploadTaskLogStepVO, taskVO);

            // 提取代码库信息并保存到数据库
            updateCodeRepository(uploadTaskLogStepVO, lastTaskLogEntity);

            // 分析成功时告警数据及告警统计数据后处理
            postHandleDefectsAndStatistic(uploadTaskLogStepVO, taskVO);

            TaskLogEntity.TaskUnit taskStep = lastTaskLogEntity.getStepArray().get(lastTaskLogEntity.getStepArray().size() - 1);

            //回调流水线扫描结果
            ISubsequentProcBizService.analyzePiplineHandleDevopsCallBack(lastTaskLogEntity, taskStep, toolName, taskVO);

            //发送websocket信息
            ISubsequentProcBizService.analyzeSendWebSocketMsg(toolConfigBaseVO, uploadTaskLogStepVO, lastTaskLogEntity, taskVO, taskId, toolName);

        }

        log.info("upload tasklog success.");
        return new CodeCCResult(CommonMessageCode.SUCCESS, "upload taskLog ok");
    }

    /**
     * 通过消息队列异步提单
     * @param uploadTaskLogStepVO
     * @param taskVO
     */
    protected void asyncCommitDefect(UploadTaskLogStepVO uploadTaskLogStepVO, TaskDetailVO taskVO)
    {
        String toolName = uploadTaskLogStepVO.getToolName();
        String toolPattern = toolMetaCacheService.getToolPattern(toolName);

        long fileSize = scmJsonComponent.getDefectFileSize(uploadTaskLogStepVO.getStreamName(),
                uploadTaskLogStepVO.getToolName(), uploadTaskLogStepVO.getPipelineBuildId());

        // TODO 兼容老流程加的逻辑，后续需要删除
        String reportKey = String.format("%s:%s:%s:%s", RedisKeyConstants.KEY_REPORT_DEFECT, uploadTaskLogStepVO.getStreamName(), toolName, uploadTaskLogStepVO.getPipelineBuildId());
        String defectReportStatus = redisTemplate.opsForValue().get(reportKey);

        // 区分创建来源为工蜂项目，创建对应处理器
        IMessageQueueBizService messageQueueBizService = MessageBizServiceFactory.createBizService(taskVO.getTaskId(),ComConstants.BusinessType.MESSAGE_QUEUE.value(),IMessageQueueBizService.class);

        // 获取exchange和routingKey对应常量值
        Map<String,String> messageQueueInfo=messageQueueBizService.getExchangAndEroutingKey(fileSize,toolPattern);
        String exchange = messageQueueInfo.get(ConstantsKt.EXCHANGE);
        String routingKey = messageQueueInfo.get(ConstantsKt.ROUTINGKEY);

        // 通过消息队列异步提单
        CommitDefectVO commitDefectVO = new CommitDefectVO();
        commitDefectVO.setTaskId(uploadTaskLogStepVO.getTaskId());
        commitDefectVO.setStreamName(uploadTaskLogStepVO.getStreamName());
        commitDefectVO.setToolName(toolName);
        commitDefectVO.setBuildId(uploadTaskLogStepVO.getPipelineBuildId());
        commitDefectVO.setTriggerFrom(uploadTaskLogStepVO.getTriggerFrom());
        rabbitTemplate.convertAndSend( exchange, routingKey, commitDefectVO);
    }

    protected void updateCodeRepository(UploadTaskLogStepVO uploadTaskLogStepVO, TaskLogEntity taskLogEntity)
    {
        if (uploadTaskLogStepVO.getStepNum() == ComConstants.Step4MutliTool.DOWNLOAD.value()
                && uploadTaskLogStepVO.getFlag() == ComConstants.StepFlag.SUCC.value()
                && StringUtils.isNotEmpty(uploadTaskLogStepVO.getMsg()))
        {
            updateCodeRepository(uploadTaskLogStepVO.getTaskId(), uploadTaskLogStepVO.getPipelineBuildId(), uploadTaskLogStepVO.getMsg());
        }
        else if (uploadTaskLogStepVO.getStepNum() == getSubmitStepNum()
                && uploadTaskLogStepVO.getFlag() == ComConstants.StepFlag.SUCC.value())
        {
            ThreadPoolUtil.addRunnableTask(() ->
            {
                List<TaskLogEntity.TaskUnit> stepArray = taskLogEntity.getStepArray();
                for(TaskLogEntity.TaskUnit step: stepArray)
                {
                    if (step.getStepNum() == getCodeDownloadStepNum() && StringUtils.isEmpty(step.getMsg()))
                    {
                        // 保存代码仓信息
                        saveCodeRepoInfo(uploadTaskLogStepVO);
                        break;
                    }
                }
            });

        }
    }

    protected void updateCodeRepository(long taskId, String buildId, String msg)
    {
        log.info("begin updateCodeRepository, taskId:{}, buildId:{}, msg:{}", taskId, buildId, msg);
        if (StringUtils.isEmpty(msg))
        {
            return;
        }

        List<JSONObject> codeRepoList = PathUtils.extractCodeRepoUrlFromMsg(msg);
        log.info("Extract Code Repo From Msg: {}", codeRepoList);
        if (codeRepoList.size() > 0)
        {
            Set<CodeRepoFromAnalyzeLogEntity.CodeRepo> codeFileUrlEntityList = JsonUtil.INSTANCE.to(codeRepoList.toString(), new TypeReference<Set<CodeRepoFromAnalyzeLogEntity.CodeRepo>>()
            {
            });
            if(CollectionUtils.isNotEmpty(codeFileUrlEntityList))
            {
                Long currentTime = System.currentTimeMillis();
                codeFileUrlEntityList.forEach(codeRepo ->
                        codeRepo.setCreateDate(currentTime)
                );
            }
            else
            {
                codeFileUrlEntityList = new HashSet<>();
            }
            CodeRepoFromAnalyzeLogEntity codeRepoFromAnalyzeLogEntity = codeRepoFromAnalyzeLogRepository.findCodeRepoFromAnalyzeLogEntityByTaskId(taskId);
            if (codeRepoFromAnalyzeLogEntity == null)
            {
                codeRepoFromAnalyzeLogEntity = new CodeRepoFromAnalyzeLogEntity();
                codeRepoFromAnalyzeLogEntity.setTaskId(taskId);
                codeRepoFromAnalyzeLogEntity.setBuildId(buildId);
                codeRepoFromAnalyzeLogEntity.setCodeRepoList(codeFileUrlEntityList);
            }
            else
            {
                //如果代码库有更新，都取最新的
                Set<CodeRepoFromAnalyzeLogEntity.CodeRepo> codeRepos = codeRepoFromAnalyzeLogEntity.getCodeRepoList();
                if(CollectionUtils.isNotEmpty(codeRepos))
                {
                    codeFileUrlEntityList.addAll(codeRepos);
                }
                codeRepoFromAnalyzeLogEntity.setCodeRepoList(codeFileUrlEntityList);
            }
            codeRepoFromAnalyzeLogRepository.save(codeRepoFromAnalyzeLogEntity);
            log.info("update code repo successfully!");
        }
    }


    /**
     * 保存代码仓信息
     * @param uploadTaskLogStepVO
     * @return
     */
    protected void saveCodeRepoInfo(UploadTaskLogStepVO uploadTaskLogStepVO)
    {
        StringBuffer scmInfoStrBuf = new StringBuffer();
        JSONArray repoInfoJsonArr = scmJsonComponent.loadRepoInfo(uploadTaskLogStepVO.getStreamName(), uploadTaskLogStepVO.getToolName(), uploadTaskLogStepVO.getPipelineBuildId());
        if (repoInfoJsonArr != null && repoInfoJsonArr.length() > 0)
        {
            Long currentTime = System.currentTimeMillis();
            Set<CodeRepoFromAnalyzeLogEntity.CodeRepo> codeRepoSet = new HashSet<>();
            for (int i = 0; i < repoInfoJsonArr.length(); i++)
            {
                JSONObject codeRepoJson = repoInfoJsonArr.getJSONObject(i);
                ScmInfoVO codeRepoInfo = JsonUtil.INSTANCE.to(codeRepoJson.toString(), ScmInfoVO.class);
                String formatUrl = PathUtils.formatRepoUrlToHttp(codeRepoInfo.getUrl());
                if (StringUtils.isNotEmpty(formatUrl))
                {
                    CodeRepoFromAnalyzeLogEntity.CodeRepo codeRepo = new CodeRepoFromAnalyzeLogEntity.CodeRepo();
                    codeRepo.setUrl(formatUrl);
                    codeRepo.setBranch(codeRepoInfo.getBranch());
                    codeRepo.setVersion(codeRepoInfo.getRevision());
                    codeRepo.setCreateDate(currentTime);
                    codeRepoSet.add(codeRepo);

                    scmInfoStrBuf.append("代码库：").append(formatUrl).append("，")
                            .append("版本号：").append(codeRepoInfo.getRevision()).append("，")
                            .append("提交时间：").append(DateTimeUtils.second2Moment(codeRepoInfo.getFileUpdateTime() / ComConstants.COMMON_NUM_1000L)).append("，")
                            .append("提交人：").append(codeRepoInfo.getFileUpdateAuthor()).append("，")
                            .append("分支：").append(codeRepoInfo.getBranch())
                            .append("\n");
                }
            }
            CodeRepoFromAnalyzeLogEntity codeRepoFromAnalyzeLogEntity =
                    codeRepoFromAnalyzeLogRepository.findCodeRepoFromAnalyzeLogEntityByTaskId(uploadTaskLogStepVO.getTaskId());
            if (codeRepoFromAnalyzeLogEntity == null)
            {
                codeRepoFromAnalyzeLogEntity = new CodeRepoFromAnalyzeLogEntity();
                codeRepoFromAnalyzeLogEntity.setTaskId(uploadTaskLogStepVO.getTaskId());
                codeRepoFromAnalyzeLogEntity.setBuildId(uploadTaskLogStepVO.getPipelineBuildId());
                codeRepoFromAnalyzeLogEntity.setCodeRepoList(codeRepoSet);
            }
            else
            {
                //如果代码库有更新，都取最新的
                Set<CodeRepoFromAnalyzeLogEntity.CodeRepo> codeRepos = codeRepoFromAnalyzeLogEntity.getCodeRepoList();
                if(CollectionUtils.isNotEmpty(codeRepos))
                {
                    codeRepoSet.addAll(codeRepos);
                }
                codeRepoFromAnalyzeLogEntity.setCodeRepoList(codeRepoSet);
            }
            codeRepoFromAnalyzeLogRepository.save(codeRepoFromAnalyzeLogEntity);
            log.info("update code repo successfully!");
        }

        if (scmInfoStrBuf.length() > 0)
        {
            TaskLogEntity taskLogEntity = taskLogRepository.findByTaskIdAndToolNameAndBuildId(uploadTaskLogStepVO.getTaskId(),
                    uploadTaskLogStepVO.getToolName(), uploadTaskLogStepVO.getPipelineBuildId());
            if (taskLogEntity != null && CollectionUtils.isNotEmpty(taskLogEntity.getStepArray()))
            {
                for (TaskLogEntity.TaskUnit step : taskLogEntity.getStepArray()) {
                    if (step.getStepNum() == getCodeDownloadStepNum()) {
                        step.setMsg(scmInfoStrBuf.toString());
                        taskLogRepository.save(taskLogEntity);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 发送websocket信息
     *
     * @param toolConfigBaseVO
     * @param uploadTaskLogStepVO
     * @param taskId
     * @param toolName
     */
    protected void sendWebSocketMsg(ToolConfigBaseVO toolConfigBaseVO, UploadTaskLogStepVO uploadTaskLogStepVO,
                                    TaskLogEntity taskLogEntity, TaskDetailVO taskDetailVO, long taskId, String toolName)
    {
        //1. 推送消息至任务详情首页面
        TaskOverviewVO.LastAnalysis lastAnalysis = assembleAnalysisResult(toolConfigBaseVO, uploadTaskLogStepVO, toolName);
        //获取告警数量信息
        if (ComConstants.Step4MutliTool.COMMIT.value() == uploadTaskLogStepVO.getStepNum() &&
                ComConstants.StepFlag.SUCC.value() == uploadTaskLogStepVO.getFlag())
        {
            ToolLastAnalysisResultVO toolLastAnalysisResultVO = new ToolLastAnalysisResultVO();
            toolLastAnalysisResultVO.setTaskId(taskId);
            toolLastAnalysisResultVO.setToolName(toolName);
            BaseLastAnalysisResultVO lastAnalysisResultVO = taskLogService.getLastAnalysisResult(toolLastAnalysisResultVO, toolName);
            lastAnalysis.setLastAnalysisResult(lastAnalysisResultVO);
        }

        TaskLogVO taskLogVO = new TaskLogVO();
        BeanUtils.copyProperties(taskLogEntity, taskLogVO, "stepArray");
        List<TaskLogEntity.TaskUnit> stepArrayEntity = taskLogEntity.getStepArray();
        List<TaskLogVO.TaskUnit> stepArrayVO = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(stepArrayEntity))
        {
            stepArrayVO = stepArrayEntity.stream().map(taskUnit -> {
                TaskLogVO.TaskUnit taskUnitVO = new TaskLogVO.TaskUnit();
                BeanUtils.copyProperties(taskUnit, taskUnitVO);
                return taskUnitVO;
            }).collect(Collectors.toList());
        }
        taskLogVO.setStepArray(stepArrayVO);

        assembleTaskInfo(uploadTaskLogStepVO, taskDetailVO, taskLogEntity);

        WebsocketDTO websocketDTO = new WebsocketDTO(taskLogVO, lastAnalysis, taskDetailVO);
        rabbitTemplate.convertAndSend(ConstantsKt.EXCHANGE_TASKLOG_DEFECT_WEBSOCKET, "",
                websocketDTO);
    }


    protected TaskOverviewVO.LastAnalysis assembleAnalysisResult(ToolConfigBaseVO toolConfigBaseVO, UploadTaskLogStepVO uploadTaskLogStepVO,
                                                                 String toolName)
    {
        TaskOverviewVO.LastAnalysis lastAnalysis = new TaskOverviewVO.LastAnalysis();
        lastAnalysis.setToolName(toolName);
        lastAnalysis.setCurStep(toolConfigBaseVO.getCurStep());
        lastAnalysis.setStepStatus(toolConfigBaseVO.getStepStatus());
        long startTime = uploadTaskLogStepVO.getStartTime();
        long endTime = uploadTaskLogStepVO.getEndTime();
        long elapseTime = uploadTaskLogStepVO.getElapseTime();
        if (elapseTime == 0L)
        {
            if(endTime != 0 && startTime != 0 && endTime > startTime)
            {
                elapseTime = endTime - startTime;
            }
        }
        lastAnalysis.setElapseTime(elapseTime);
        lastAnalysis.setLastAnalysisTime(endTime != 0 ? endTime : startTime);
        return lastAnalysis;
    }

    protected void assembleTaskInfo(UploadTaskLogStepVO uploadTaskLogStepVO, TaskDetailVO taskDetailVO,
                                    TaskLogEntity taskLogEntity)
    {
        List<ToolConfigInfoVO> toolConfigInfoVOList = taskDetailVO.getToolConfigInfoList();
        if(CollectionUtils.isNotEmpty(toolConfigInfoVOList))
        {
            Integer totalFinishStep = 0;
            Integer totalStep = 0;
            Boolean processFlag = false;
            Long minStartTime = Long.MAX_VALUE;
            for(ToolConfigInfoVO toolConfigInfoVO : toolConfigInfoVOList)
            {

                if (ComConstants.FOLLOW_STATUS.WITHDRAW.value() != toolConfigInfoVO.getFollowStatus())
                {
                    // 获取工具展示名称
                    ToolMetaBaseVO toolMetaBaseVO = toolMetaCacheService.getToolBaseMetaCache(toolConfigInfoVO.getToolName());
                    //添加进度条
                    if(toolConfigInfoVO.getToolName().equalsIgnoreCase(taskLogEntity.getToolName()))
                    {
                        Integer curStep = taskLogEntity.getCurrStep();
                        int submitStepNum = getSubmitStepNum();
                        if (taskLogEntity.getCurrStep() == submitStepNum && taskLogEntity.getEndTime() != 0)
                        {
                            curStep = submitStepNum + 1;
                        }
                        toolConfigInfoVO.setCurStep(curStep);
                        toolConfigInfoVO.setStepStatus(uploadTaskLogStepVO.getFlag() == ComConstants.StepFlag.FAIL.value() ?
                                ComConstants.StepStatus.FAIL.value() : ComConstants.StepStatus.SUCC.value());
                        toolConfigInfoVO.setStartTime(taskLogEntity.getStartTime());
                        totalFinishStep += toolConfigInfoVO.getCurStep();
                    }
                    else
                    {
                        if(StringUtils.isNotBlank(taskLogEntity.getBuildId()))
                        {
                            if(taskLogEntity.getBuildId().equalsIgnoreCase(toolConfigInfoVO.getCurrentBuildId()))
                            {
                                totalFinishStep += toolConfigInfoVO.getCurStep();
                            }
                        }
                    }

                    switch (toolMetaBaseVO.getPattern())
                    {
                        case "LINT":
                            totalStep += 5;
                            break;
                        case "CCN":
                            totalStep += 5;
                            break;
                        case "DUPC":
                            totalStep += 5;
                            break;
                        default:
                            totalStep += 6;
                            break;
                    }

                    minStartTime = Math.min(minStartTime, toolConfigInfoVO.getStartTime());

                    if(!processFlag){
                        processFlag = taskDetailDisplayInfo(toolConfigInfoVO, taskDetailVO, toolMetaBaseVO.getDisplayName());
                    }
                }
            }
            if(totalStep == 0)
            {
                taskDetailVO.setDisplayProgress(0);
            }
            else
            {
                if(totalFinishStep > totalStep){
                    totalFinishStep = totalStep;
                }
                taskDetailVO.setDisplayProgress(totalFinishStep * 100 / totalStep);
            }
            if(null == taskDetailVO.getDisplayStepStatus())
            {
                taskDetailVO.setDisplayStepStatus(ComConstants.StepStatus.SUCC.value());
            }
            if(minStartTime < Long.MAX_VALUE)
            {
                taskDetailVO.setMinStartTime(minStartTime);
            }
            else
            {
                taskDetailVO.setMinStartTime(0L);
            }
        }
    }



    private Boolean taskDetailDisplayInfo(ToolConfigInfoVO toolConfigInfoEntity, TaskDetailVO taskDetailVO, String displayName)
    {
        Integer displayStepStatus = 0;
        //检测到有任务运行中（非成功状态）
        Boolean processFlag = false;
        //更新工具显示状态
        //如果有失败的工具，则显示失败的状态
        if (toolConfigInfoEntity.getStepStatus() == ComConstants.StepStatus.FAIL.value())
        {
            displayStepStatus = ComConstants.StepStatus.FAIL.value();
            taskDetailVO.setDisplayStepStatus(displayStepStatus);
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果没找到失败的工具，有分析中的工具，则显示分析中
        else if (toolConfigInfoEntity.getStepStatus() == ComConstants.StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() < ComConstants.Step4MutliTool.COMPLETE.value() &&
                toolConfigInfoEntity.getCurStep() > ComConstants.Step4MutliTool.READY.value() &&
                displayStepStatus != ComConstants.StepStatus.FAIL.value())
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果没找到失败的工具，有准备的工具，则显示准备
        else if (toolConfigInfoEntity.getStepStatus() == ComConstants.StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() == ComConstants.Step4MutliTool.READY.value() &&
                displayStepStatus != ComConstants.StepStatus.FAIL.value())
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果还没找到其他状态，则显示成功
        else if (toolConfigInfoEntity.getStepStatus() == ComConstants.StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() >= ComConstants.Step4MutliTool.COMPLETE.value() &&
                StringUtils.isBlank(taskDetailVO.getDisplayToolName()))
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
        }
        return processFlag;

    }

    /**
     * 分析开始前告警数据及告警统计数据预处理
     *
     * @param uploadTaskLogStepVO
     * @param analysisVersion
     */
    protected abstract void preHandleDefectsAndStatistic(UploadTaskLogStepVO uploadTaskLogStepVO, String analysisVersion);

    /**
     * 分析成功时告警数据及告警统计数据后处理
     *
     * @param uploadTaskLogStepVO
     */
    protected abstract void postHandleDefectsAndStatistic(UploadTaskLogStepVO uploadTaskLogStepVO, TaskDetailVO taskVO);

    /**
     * 更分析记录
     *
     * @param taskLogEntity
     * @param uploadTaskLogStepVO
     * @param taskVO
     */
    private ToolConfigBaseVO updateTaskLog(TaskLogEntity taskLogEntity, UploadTaskLogStepVO uploadTaskLogStepVO, TaskBaseVO taskVO)
    {
        appendStepInfo(taskLogEntity, uploadTaskLogStepVO, taskVO);
        taskLogRepository.save(taskLogEntity);

        return updateToolStatus(taskLogEntity);
    }

    /**
     * 判断是否是虚拟流水线（即蓝盾CodecCC服务创建的流水线）的排队步骤
     * 如果是虚拟流水线CodeCC服务手动触发的任务，且步骤为第一步（排队），则不需要创建新任务
     * 因为CodeCC服务手动触发的任务，为了实现触发后页面能立即看到任务步骤，会先调用分析记录上报接口发送排队开始步骤，那时已经创建了新任务
     * 后面流水线调用工具侧分析脚本，工具侧又会报一次上传开始步骤，这里对这个步骤不作处理。
     *
     * @return
     */
    private boolean isQueueStepOfVirtualpipeline(String createFrom, String triggerFrom, int stepNum)
    {
        return ComConstants.BsTaskCreateFrom.BS_CODECC.value().equals(createFrom) && StringUtils.isNotEmpty(triggerFrom)
                && stepNum == ComConstants.Step4MutliTool.QUEUE.value();
    }

    /**
     * 将本次上报的分析记录步骤追加到最近的一次分析记录里面
     *
     * @param taskLogEntity
     * @param uploadTaskLogStepVO
     * @param taskVO
     */
    private void appendStepInfo(TaskLogEntity taskLogEntity, UploadTaskLogStepVO uploadTaskLogStepVO, TaskBaseVO taskVO)
    {
        TaskLogEntity.TaskUnit taskStep = new TaskLogEntity.TaskUnit();
        BeanUtils.copyProperties(uploadTaskLogStepVO, taskStep);

        // 加入扫描类型是增量还是全量
        setScanType(taskStep, uploadTaskLogStepVO.getTaskId(), uploadTaskLogStepVO.getToolName(), uploadTaskLogStepVO.getPipelineBuildId(), taskVO);

        taskLogEntity.setCurrStep(taskStep.getStepNum());
        if (taskStep.getFlag() == ComConstants.StepFlag.PROCESSING.value())
        {
            /*若为蓝盾CodeCC服务手动触发的多工具任务，且步骤为第一步-排队（CodeCC服务手动触发的任务，为了实现触发后页面能立即看到任务步骤，会先调用
              MutiToolAnalyzeTaskAction类发送排队开始步骤，后面流水线调用工具侧分析脚本，工具侧又会报一次上传开始步骤，这里对这个步骤不作处理。*/
            if (ComConstants.BsTaskCreateFrom.BS_CODECC.value().equals(taskVO.getCreateFrom())
                    && taskStep.getStepNum() == ComConstants.Step4MutliTool.QUEUE.value())
            {
                return;
            }

            //该步骤是进行中的状态，直接添加到任务数组里
            taskLogEntity.getStepArray().add(taskStep);
            taskLogEntity.setFlag(ComConstants.StepFlag.PROCESSING.value());
        }
        //该步骤不是进行中状态，则为结束，从任务里拿出最后一个步骤进行修改
        else
        {
            TaskLogEntity.TaskUnit lastTaskStep = taskLogEntity.getStepArray().get(taskLogEntity.getStepArray().size() - 1);
            if (lastTaskStep.getStepNum() == taskStep.getStepNum())
            {
                lastTaskStep.setFlag(taskStep.getFlag());
                lastTaskStep.setEndTime(taskStep.getEndTime());
                lastTaskStep.setMsg(taskStep.getMsg());
                lastTaskStep.setElapseTime(taskStep.getEndTime() - lastTaskStep.getStartTime());
            }
            else
            {
                taskLogEntity.getStepArray().add(taskStep);
                lastTaskStep.setFlag(ComConstants.StepFlag.SUCC.value());
            }
        }

        //满足以下条件可以将任务设置为结束并计算耗时
        taskFinishSetting(taskLogEntity, taskStep);
        uploadTaskLogStepVO.setElapseTime(taskLogEntity.getElapseTime());
    }

    private void setScanType(TaskLogEntity.TaskUnit taskStep, long taskId, String toolName, String buildId, TaskBaseVO taskVO)
    {
        String pattern = toolMetaCacheService.getToolPattern(toolName);
        if (ComConstants.ToolPattern.LINT.name().equals(pattern) || ComConstants.ToolPattern.CCN.name().equals(pattern))
        {
            if (taskStep.getStepNum() == ComConstants.Step4MutliTool.SCAN.value())
            {
                ToolBuildStackEntity toolBuildStackEntity = toolBuildStackRepository.findByTaskIdAndToolNameAndBuildId(taskId, toolName, buildId);
                boolean isFullScan = toolBuildStackEntity != null ? toolBuildStackEntity.isFullScan() : true;
                String scanTypeMsg = "";
                if (taskVO.getScanType() != null && taskVO.getScanType() == ComConstants.ScanType.DIFF_MODE.code) {
                    scanTypeMsg = "MR/PR扫描";
                } else if (isFullScan) {
                    scanTypeMsg = "全量扫描";
                } else {
                    scanTypeMsg = "增量扫描";
                }
                if (StringUtils.isEmpty(taskStep.getMsg()))
                {
                    taskStep.setMsg(scanTypeMsg);
                }
                else if (!taskStep.getMsg().contains(scanTypeMsg))
                {
                    taskStep.setMsg("(" + scanTypeMsg + ")" + taskStep.getMsg());
                }
            }
        }
    }

    /**
     * 满足以下条件可以将任务设置为结束并计算耗时
     * （1）步骤是失败的
     * （2）步骤是最后一步并且设置了结束时间
     *
     * @param taskLogEntity
     * @param taskStep
     */
    public void taskFinishSetting(TaskLogEntity taskLogEntity, TaskLogEntity.TaskUnit taskStep)
    {
        if (taskStep.getFlag() == ComConstants.StepFlag.FAIL.value()
                || (taskStep.getFlag() == ComConstants.StepFlag.SUCC.value() && taskStep.getStepNum() == getSubmitStepNum()))
        {
            taskLogEntity.setEndTime(taskStep.getEndTime());
            taskLogEntity.setFlag(taskStep.getFlag());
            if (taskLogEntity.getStartTime() != ComConstants.COMMON_NUM_0L)
            {
                taskLogEntity.setElapseTime(taskLogEntity.getEndTime() - taskLogEntity.getStartTime());
            }
            else
            {
                for (TaskLogEntity.TaskUnit taskUnit : taskLogEntity.getStepArray())
                {
                    if (taskUnit.getStartTime() != ComConstants.COMMON_NUM_0L)
                    {
                        taskLogEntity.setElapseTime(taskLogEntity.getEndTime() - taskUnit.getStartTime());
                        taskLogEntity.setStartTime(taskUnit.getStartTime());
                        break;
                    }
                }
            }
        }

    }

    private boolean needCreateTaskWhenSameStep(TaskLogEntity lastTask, UploadTaskLogStepVO taskStep, TaskBaseVO taskBaseVO)
    {
        if (lastTask.getCurrStep() != taskStep.getStepNum())
        {
            return false;
        }

        TaskLogEntity.TaskUnit lastTaskStep = lastTask.getStepArray().get(lastTask.getStepArray().size() - 1);

        //若为蓝盾CodeCC服务手动触发的任务，且步骤为第一步-排队，则不需要创建新任务
        if (isQueueStepOfVirtualpipeline(taskBaseVO.getCreateFrom(), lastTask.getTriggerFrom(), lastTaskStep.getStepNum()))
        {
            return false;
        }

        //同时拥有开始时间
        if (lastTaskStep.getStartTime() > 0 && taskStep.getStartTime() > 0)
        {
            return true;
        }

        //同时拥有结束时间
        if (lastTaskStep.getEndTime() > 0 && taskStep.getEndTime() > 0)
        {
            return true;
        }

        //上一个有结束时间，这个有开始时间
        if (lastTaskStep.getEndTime() > 0 && taskStep.getStartTime() > 0)
        {
            return true;
        }

        return false;
    }

    /**
     * 创建新的分析记录
     *
     * @param uploadTaskLogStepVO
     * @param taskBaseVO
     * @return
     */
    public TaskLogEntity createNewTaskLog(UploadTaskLogStepVO uploadTaskLogStepVO, TaskBaseVO taskBaseVO, ToolConfigBaseVO toolConfigBaseVO)
    {
        if (uploadTaskLogStepVO.getStartTime() == 0)
        {
            uploadTaskLogStepVO.setStartTime(System.currentTimeMillis());
        }
        TaskLogEntity taskLogEntity = new TaskLogEntity();
        taskLogEntity.setTaskId(taskBaseVO.getTaskId());
        taskLogEntity.setStreamName(taskBaseVO.getNameEn());
        taskLogEntity.setToolName(uploadTaskLogStepVO.getToolName());
        taskLogEntity.setStartTime(uploadTaskLogStepVO.getStartTime() == 0 ? System.currentTimeMillis() : uploadTaskLogStepVO.getStartTime());
        taskLogEntity.setCurrStep(uploadTaskLogStepVO.getStepNum());
        taskLogEntity.setFlag(uploadTaskLogStepVO.getFlag());
        if (taskLogEntity.getFlag() == ComConstants.StepFlag.FAIL.value())
        {
            taskLogEntity.setEndTime(taskLogEntity.getStartTime());
        }

        //保存蓝盾项目流水线id，构建id，蓝盾codecc服务创建的多工具任务手动触发的触发人
        taskLogEntity.setPipelineId(taskBaseVO.getPipelineId());
        taskLogEntity.setBuildId(uploadTaskLogStepVO.getPipelineBuildId());
        taskLogEntity.setTriggerFrom(uploadTaskLogStepVO.getTriggerFrom());

        TaskLogEntity.TaskUnit taskStep = new TaskLogEntity.TaskUnit();
        BeanUtils.copyProperties(uploadTaskLogStepVO, taskStep);

        List<TaskLogEntity.TaskUnit> stepArray = new ArrayList<>();
        stepArray.add(taskStep);
        taskLogEntity.setStepArray(stepArray);

        // 1.如果是开始构建步骤，需要把触发方式及触发人信息填入步骤信息里面
        setTriggerInfo(taskStep, taskBaseVO);

        // 2.如果是流水线任务，则查询流水线的构建信息并且保存到codecc本地
        String buildNum = saveBuildInfo(taskLogEntity);
        taskLogEntity.setBuildNum(buildNum);

        // 3.保存分析记录
        log.info("save task log");
        taskLogRepository.save(taskLogEntity);

        // 4.调用task模块的接口更新工具的步骤状态
        ToolConfigBaseVO finalToolConfigBaseVO = updateToolStatus(taskLogEntity);
        BeanUtils.copyProperties(finalToolConfigBaseVO, toolConfigBaseVO);
        return taskLogEntity;
    }

    /**
     * @param taskLogEntity
     */
    private String saveBuildInfo(TaskLogEntity taskLogEntity)
    {
        String buildNum = null;
        String pipeline = taskLogEntity.getPipelineId();
        String buildId = taskLogEntity.getBuildId();
        if (StringUtils.isNotEmpty(pipeline) && StringUtils.isNotEmpty(buildId))
        {
            BuildEntity buildEntity = buildRepository.findByBuildId(buildId);
            if (buildEntity == null)
            {
                BuildEntity buildInfo = pipelineService.getBuildIdInfo(buildId);
                if (buildInfo != null)
                {
                    buildRepository.save(buildInfo);
                    buildNum = buildInfo.getBuildNo();
                }
            }
            else
            {
                buildNum = buildEntity.getBuildNo();
            }
            log.info("save build info finish, buildNum is {}", buildNum);
        }

        return buildNum;
    }

    /**
     * 处理蓝盾coverity任务详情订制化需求
     * 1、对于蓝盾流水线创建的codecc任务，在构建第一步信息中要标识为：由流水线_XXX触发。
     * 2、对于codecc服务创建的codecc任务，在构建第一步信息中需要标识是定时触发还是手动触发
     *
     * @param taskStep
     * @param taskBaseVO
     */
    private void setTriggerInfo(TaskLogEntity.TaskUnit taskStep, TaskBaseVO taskBaseVO)
    {
        //不是蓝盾项目的任务 或者任务步骤不是第一步不处理
        if (taskStep.getStepNum() != ComConstants.Step4MutliTool.QUEUE.value())
        {
            return;
        }

        String triggerInfo = "";

        //蓝盾流水线创建的codecc任务
        if (ComConstants.BsTaskCreateFrom.BS_PIPELINE.value().equals(taskBaseVO.getCreateFrom()))
        {
            triggerInfo = "流水线（" + taskBaseVO.getNameCn() + "）触发";
        }
        //codecc服务创建的codecc任务
        else if (ComConstants.BsTaskCreateFrom.BS_CODECC.value().equals(taskBaseVO.getCreateFrom()))
        {
            String msg = taskStep.getMsg();
            if (StringUtils.isEmpty(msg))
            {
                triggerInfo = "定时触发";
            }
            else
            {
                triggerInfo = msg;
            }
        }

        taskStep.setMsg(triggerInfo);
    }

    /**
     * 更新中断的分析任务数据
     *
     * @param taskLogEntity
     * @param toolName
     */
    public ToolConfigBaseVO updateAbortAnalysisTaskLog(TaskLogEntity taskLogEntity, String toolName, String msg)
    {
        int lastIndex = taskLogEntity.getStepArray().size() - 1;
        taskLogEntity.setFlag(ComConstants.StepFlag.FAIL.value());
        TaskLogEntity.TaskUnit lastTaskStep;
        if (lastIndex >= 0)
        {
            lastTaskStep = taskLogEntity.getStepArray().get(lastIndex);
            lastTaskStep.setFlag(ComConstants.StepFlag.ABORT.value());
            lastTaskStep.setMsg(msg);
        }
        taskLogRepository.save(taskLogEntity);

        // 调用task模块的接口更新工具的步骤状态
        return updateToolStatus(taskLogEntity);
    }

    /**
     * 调用task模块的接口更新工具的状态
     *
     * @param taskLogEntity
     */
    private ToolConfigBaseVO updateToolStatus(TaskLogEntity taskLogEntity)
    {
        int curStep = taskLogEntity.getCurrStep();
        int submitStepNum = getSubmitStepNum();
        if (taskLogEntity.getCurrStep() == submitStepNum && taskLogEntity.getEndTime() != 0)
        {
            curStep = submitStepNum + 1;
        }

        ToolConfigBaseVO toolConfigBaseVO = new ToolConfigBaseVO();
        toolConfigBaseVO.setTaskId(taskLogEntity.getTaskId());
        toolConfigBaseVO.setToolName(taskLogEntity.getToolName());

        toolConfigBaseVO.setCurStep(curStep);
        int stepStatus = taskLogEntity.getFlag() == ComConstants.StepFlag.FAIL.value() ? ComConstants.StepStatus.FAIL.value() : ComConstants.StepStatus.SUCC.value();
        toolConfigBaseVO.setStepStatus(stepStatus);
        toolConfigBaseVO.setCurrentBuildId(taskLogEntity.getBuildId());
        client.get(ServiceToolRestResource.class).updateToolStepStatus(toolConfigBaseVO);
        return toolConfigBaseVO;
    }

    /**
     * 保存首次分析成功时间
     *
     * @param taskId
     * @param toolName
     */
    public void saveFirstSuccessAnalyszeTime(long taskId, String toolName)
    {
        //设置首次成功扫描完成时间为区分文件新增与历史的时间
        FirstAnalysisSuccessEntity firstSuccessTimeEntity = firstSuccessTimeRepository.findFirstByTaskIdAndToolName(taskId, toolName);
        if (firstSuccessTimeEntity == null)
        {
            firstSuccessTimeEntity = new FirstAnalysisSuccessEntity();
            firstSuccessTimeEntity.setTaskId(taskId);
            firstSuccessTimeEntity.setToolName(toolName);
            firstSuccessTimeEntity.setFirstAnalysisSuccessTime(System.currentTimeMillis());
            firstSuccessTimeRepository.save(firstSuccessTimeEntity);
            log.debug("lint task first analysis success:{}, {}", taskId, toolName);
        }
    }

    /**
     * 获取提单步骤的值，子类必须实现这个方法
     * 普通工具有4个分析步骤：1：代码下载，2、代码下载；3：代码扫描，4：代码缺陷提交
     * Klocwork/Coverity有5个分析步骤：1：上传，2：排队状态，3、分析中；4：缺陷提交，5：提单
     *
     * @return
     */
    public abstract int getSubmitStepNum();


    /**
     * 获取提单步骤的值，子类必须实现这个方法
     * 普通工具有4个分析步骤：1：代码下载，2、代码下载；3：代码扫描，4：代码缺陷提交
     * Klocwork/Coverity有5个分析步骤：1：上传，2：排队状态，3、分析中；4：缺陷提交，5：提单
     *
     * @return
     */
    public abstract int getCodeDownloadStepNum();

    public void saveBuildDefects(UploadTaskLogStepVO uploadTaskLogStepVO)
    {
        List<DefectEntity> defectList = defectRepository.findByTaskIdAndToolName(uploadTaskLogStepVO.getTaskId(), uploadTaskLogStepVO.getToolName());
        if (CollectionUtils.isNotEmpty(defectList))
        {
            List<BuildDefectEntity> buildDefectEntities = Lists.newArrayList();
            for (DefectEntity defectEntity : defectList)
            {
                if (ComConstants.DefectStatus.NEW.value() != defectEntity.getStatus())
                {
                    continue;
                }
                BuildDefectEntity buildDefectEntity = new BuildDefectEntity();
                buildDefectEntity.setTaskId(uploadTaskLogStepVO.getTaskId());
                buildDefectEntity.setToolName(uploadTaskLogStepVO.getToolName());
                buildDefectEntity.setBuildId(uploadTaskLogStepVO.getPipelineBuildId());
                buildDefectEntity.setDefectId(defectEntity.getId());
                buildDefectEntities.add(buildDefectEntity);
            }
            buildDefectRepository.save(buildDefectEntities);
        }
    }

    public void clearForceFullScan(long taskId, String toolName)
    {
        // 清除强制全量扫描标志
        toolBuildInfoDao.clearForceFullScan(taskId, toolName);
    }
}
