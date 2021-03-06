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

package com.tencent.bk.codecc.task.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tencent.bk.codecc.defect.api.ServiceCheckerSetRestResource;
import com.tencent.bk.codecc.defect.api.ServiceTaskLogRestResource;
import com.tencent.bk.codecc.defect.api.ServiceToolBuildInfoResource;
import com.tencent.bk.codecc.defect.dto.ScanTaskTriggerDTO;
import com.tencent.bk.codecc.defect.vo.common.AuthorTransferVO;
import com.tencent.bk.codecc.quartz.pojo.JobExternalDto;
import com.tencent.bk.codecc.quartz.pojo.OperationType;
import com.tencent.bk.codecc.task.constant.TaskConstants;
import com.tencent.bk.codecc.task.constant.TaskMessageCode;
import com.tencent.bk.codecc.task.dao.CommonDao;
import com.tencent.bk.codecc.task.dao.mongorepository.TaskRepository;
import com.tencent.bk.codecc.task.dao.mongorepository.ToolRepository;
import com.tencent.bk.codecc.task.dao.mongotemplate.TaskDao;
import com.tencent.bk.codecc.task.enums.ProjectLanguage;
import com.tencent.bk.codecc.task.enums.TaskSortType;
import com.tencent.bk.codecc.task.model.DisableTaskEntity;
import com.tencent.bk.codecc.task.model.NewDefectJudgeEntity;
import com.tencent.bk.codecc.task.model.NotifyCustomEntity;
import com.tencent.bk.codecc.task.model.TaskInfoEntity;
import com.tencent.bk.codecc.task.model.ToolConfigInfoEntity;
import com.tencent.bk.codecc.task.pojo.TofOrganizationInfo;
import com.tencent.bk.codecc.task.pojo.TofStaffInfo;
import com.tencent.bk.codecc.task.service.*;
import com.tencent.bk.codecc.task.tof.TofClientApi;
import com.tencent.bk.codecc.task.utils.PageableUtils;
import com.tencent.bk.codecc.task.vo.*;
import com.tencent.bk.codecc.task.vo.checkerset.ToolCheckerSetVO;
import com.tencent.bk.codecc.task.vo.pipeline.PipelineTaskVO;
import com.tencent.bk.codecc.task.vo.pipeline.PipelineToolParamVO;
import com.tencent.bk.codecc.task.vo.pipeline.PipelineToolVO;
import com.tencent.bk.codecc.task.vo.scanconfiguration.NewDefectJudgeVO;
import com.tencent.bk.codecc.task.vo.scanconfiguration.ScanConfigurationVO;
import com.tencent.bk.codecc.task.vo.scanconfiguration.TimeAnalysisConfigVO;
import com.tencent.bk.codecc.task.vo.tianyi.QueryMyTasksReqVO;
import com.tencent.bk.codecc.task.vo.tianyi.TaskInfoVO;
import com.tencent.devops.common.api.GetLastAnalysisResultsVO;
import com.tencent.devops.common.api.QueryTaskListReqVO;
import com.tencent.devops.common.api.ToolMetaBaseVO;
import com.tencent.devops.common.api.analysisresult.ToolLastAnalysisResultVO;
import com.tencent.devops.common.api.checkerset.CheckerSetVO;
import com.tencent.devops.common.api.exception.CodeCCException;
import com.tencent.devops.common.api.exception.StreamException;
import com.tencent.devops.common.api.exception.UnauthorizedException;
import com.tencent.devops.common.api.pojo.GlobalMessage;
import com.tencent.devops.common.api.pojo.Page;
import com.tencent.devops.common.api.pojo.CodeCCResult;
import com.tencent.devops.common.auth.api.external.AuthExPermissionApi;
import com.tencent.devops.common.auth.api.external.AuthExRegisterApi;
import com.tencent.devops.common.auth.api.pojo.external.AuthRole;
import com.tencent.devops.common.auth.api.pojo.external.CodeCCAuthAction;
import com.tencent.devops.common.auth.api.pojo.external.PipelineAuthAction;
import com.tencent.devops.common.auth.api.util.PermissionUtil;
import com.tencent.devops.common.client.Client;
import com.tencent.devops.common.constant.ComConstants;
import com.tencent.devops.common.constant.CommonMessageCode;
import com.tencent.devops.common.service.ToolMetaCacheService;
import com.tencent.devops.common.service.utils.GlobalMessageUtil;
import com.tencent.devops.common.util.DateTimeUtils;
import com.tencent.devops.common.util.JsonUtil;
import com.tencent.devops.common.util.List2StrUtil;
import com.tencent.devops.common.util.ListSortUtil;
import com.tencent.devops.common.web.aop.annotation.OperationHistory;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

import static com.tencent.devops.common.api.auth.CodeCCHeaderKt.CODECC_AUTH_HEADER_DEVOPS_PROJECT_ID;
import static com.tencent.devops.common.api.auth.CodeCCHeaderKt.CODECC_AUTH_HEADER_DEVOPS_TASK_ID;
import static com.tencent.devops.common.constant.ComConstants.*;
import static com.tencent.devops.common.constant.RedisKeyConstants.GLOBAL_TOOL_PARAMS_LABEL_NAME;
import static com.tencent.devops.common.constant.RedisKeyConstants.GLOBAL_TOOL_PARAMS_TIPS;
import static com.tencent.devops.common.web.mq.ConstantsKt.EXCHANGE_EXPIRED_TASK_STATUS;
import static com.tencent.devops.common.web.mq.ConstantsKt.EXCHANGE_EXTERNAL_JOB;
import static com.tencent.devops.common.web.mq.ConstantsKt.ROUTE_EXPIRED_TASK_STATUS;

/**
 * 任务服务实现类
 *
 * @version V1.0
 * @date 2019/4/23
 */
@Service
@Slf4j
public class TaskServiceImpl implements TaskService
{
    @Autowired
    private AuthExPermissionApi authExPermissionApi;

    @Autowired
    private AuthExRegisterApi authExRegisterApi;

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private ToolMetaCacheService toolMetaCache;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private IAuthorTransferBizService authorTransferBizService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private CommonDao commonDao;

    @Autowired
    private TaskDao taskDao;

    @Autowired
    private Client client;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GlobalMessageUtil globalMessageUtil;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserManageService userManageService;

    @Autowired
    private TofClientApi tofClientApi;

    @Override
    public TaskListVO getTaskList(String projectId, String user, TaskSortType taskSortType, TaskListReqVO taskListReqVO)
    {

        Set<TaskInfoEntity> resultTasks = getQualifiedTaskList(projectId, user, null,
                null != taskListReqVO ? taskListReqVO.getTaskSource() : null);

        final String toolIdsOrder = commonDao.getToolOrder();

        List<TaskDetailVO> taskDetailVOList = resultTasks.stream().
                filter(taskInfoEntity ->
                        StringUtils.isNotEmpty(taskInfoEntity.getToolNames()) &&
                                //流水线停用任务不展示
                                !(taskInfoEntity.getStatus().equals(TaskConstants.TaskStatus.DISABLE.value()) &&
                                        BsTaskCreateFrom.BS_PIPELINE.value().equalsIgnoreCase(taskInfoEntity.getCreateFrom()))).
                map(taskInfoEntity ->
                {
                    TaskDetailVO taskDetailVO = new TaskDetailVO();
                    taskDetailVO.setTaskId(taskInfoEntity.getTaskId());
                    taskDetailVO.setToolNames(taskInfoEntity.getToolNames());
                    return taskDetailVO;
                }).
                collect(Collectors.toList());

        CodeCCResult<Map<String, List<ToolLastAnalysisResultVO>>> taskAndTaskLogCodeCCResult = client.get(ServiceTaskLogRestResource.class).
                getBatchTaskLatestTaskLog(taskDetailVOList);
        Map<String, List<ToolLastAnalysisResultVO>> taskAndTaskLogMap;
        if (taskAndTaskLogCodeCCResult.isOk() &&
                MapUtils.isNotEmpty(taskAndTaskLogCodeCCResult.getData()))
        {
            taskAndTaskLogMap = taskAndTaskLogCodeCCResult.getData();
        }
        else
        {
            log.error("get batch task log fail or task log is empty!");
            taskAndTaskLogMap = new HashMap<>();
        }


        //对工具清单进行处理
        List<TaskDetailVO> taskDetailVOS = resultTasks.stream().
                filter(taskInfoEntity ->
                        //流水线停用任务不展示
                        !(taskInfoEntity.getStatus().equals(TaskConstants.TaskStatus.DISABLE.value()) &&
                                BsTaskCreateFrom.BS_PIPELINE.value().equalsIgnoreCase(taskInfoEntity.getCreateFrom()))).
                map(taskInfoEntity ->
                {
                    TaskDetailVO taskDetailVO = new TaskDetailVO();
                    BeanUtils.copyProperties(taskInfoEntity, taskDetailVO, "toolConfigInfoList");
                    //设置置顶标识
                    Set<String> topUsers = taskInfoEntity.getTopUser();
                    if (CollectionUtils.isNotEmpty(topUsers) && topUsers.contains(user))
                    {
                        taskDetailVO.setTopFlag(1);
                    }
                    else
                    {
                        taskDetailVO.setTopFlag(-1);
                    }
                    List<ToolConfigInfoEntity> toolConfigInfoEntityList = taskInfoEntity.getToolConfigInfoList();
                    //获取分析完成时间
                    List<ToolLastAnalysisResultVO> taskLogGroupVOs = new ArrayList<>();
                    String toolNames = taskInfoEntity.getToolNames();
                    if (StringUtils.isNotEmpty(toolNames))
                    {
                        if (MapUtils.isNotEmpty(taskAndTaskLogMap))
                        {
                            taskLogGroupVOs = taskAndTaskLogMap.get(String.valueOf(taskInfoEntity.getTaskId()));
                            if (null == taskLogGroupVOs)
                            {
                                taskLogGroupVOs = new ArrayList<>();
                            }
                        }
                    }

                    if (CollectionUtils.isNotEmpty(toolConfigInfoEntityList))
                    {
                        List<ToolConfigInfoVO> toolConfigInfoVOList = new ArrayList<>();
                        boolean isAllSuspended = true;
                        Long minStartTime = Long.MAX_VALUE;
                        Boolean processFlag = false;
                        Integer totalFinishStep = 0;
                        Integer totalStep = 0;
                        for (ToolConfigInfoEntity toolConfigInfoEntity : toolConfigInfoEntityList)
                        {

                            if (null == toolConfigInfoEntity || StringUtils.isEmpty(toolConfigInfoEntity.getToolName()))
                            {
                                continue;
                            }

                            // 获取工具展示名称
//                            String displayName = toolMetaCache.getToolDisplayName(toolConfigInfoEntity.getToolName());

                            // 获取工具展示名称
                            ToolMetaBaseVO toolMetaBaseVO = toolMetaCache.getToolBaseMetaCache(toolConfigInfoEntity.getToolName());



                            if (toolConfigInfoEntity.getFollowStatus() !=
                                    ComConstants.FOLLOW_STATUS.WITHDRAW.value()){

                                //更新工具显示状态
                                //如果有失败的工具，则显示失败的状态
                                if(!processFlag){
                                    processFlag = taskDetailDisplayInfo(toolConfigInfoEntity, taskDetailVO, toolMetaBaseVO.getDisplayName());
                                }
                                //添加进度条
                                totalFinishStep += toolConfigInfoEntity.getCurStep();
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

                            }


                            ToolConfigInfoVO toolConfigInfoVO = new ToolConfigInfoVO();
                            BeanUtils.copyProperties(toolConfigInfoEntity, toolConfigInfoVO);

                            //设置分析完成时间
                            for (ToolLastAnalysisResultVO toolLastAnalysisResultVO : taskLogGroupVOs)
                            {
                                if (toolLastAnalysisResultVO.getToolName().equalsIgnoreCase(toolConfigInfoVO.getToolName()))
                                {
                                    toolConfigInfoVO.setEndTime(toolLastAnalysisResultVO.getEndTime());
                                    toolConfigInfoVO.setStartTime(toolLastAnalysisResultVO.getStartTime());
                                }
                            }
                            minStartTime = Math.min(minStartTime, toolConfigInfoVO.getStartTime());


                            if (StringUtils.isNotEmpty(toolMetaBaseVO.getDisplayName()))
                            {
                                toolConfigInfoVO.setDisplayName(toolMetaBaseVO.getDisplayName());
                            }

                            if (toolConfigInfoVO.getFollowStatus() !=
                                    ComConstants.FOLLOW_STATUS.WITHDRAW.value())
                            {
                                isAllSuspended = false;
                            }
                            if (toolConfigInfoEntity.getCheckerSet() != null)
                            {
                                ToolCheckerSetVO checkerSetVO = new ToolCheckerSetVO();
                                BeanUtils.copyProperties(toolConfigInfoEntity.getCheckerSet(), checkerSetVO);
                                toolConfigInfoVO.setCheckerSet(checkerSetVO);
                            }
                            toolConfigInfoVOList.add(toolConfigInfoVO);
                        }
                        if (isAllSuspended)
                        {
                            log.info("all tool is suspended! task id: {}", taskInfoEntity.getTaskId());
                            if(CollectionUtils.isNotEmpty(toolConfigInfoVOList))
                            {
                                toolConfigInfoVOList.get(0).setFollowStatus(ComConstants.FOLLOW_STATUS.EXPERIENCE.value());
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
                            taskDetailVO.setDisplayStepStatus(StepStatus.SUCC.value());
                        }
                        if(minStartTime < Long.MAX_VALUE)
                        {
                            taskDetailVO.setMinStartTime(minStartTime);
                        }
                        else
                        {
                            taskDetailVO.setMinStartTime(0L);
                        }
                        log.info("handle tool list finish! task id: {}", taskInfoEntity.getTaskId());
                        taskDetailVO.setToolConfigInfoList(toolConfigInfoVOList);
                    }
                    else
                    {
                        log.info("tool list is empty! task id: {}", taskInfoEntity.getTaskId());
                        taskDetailVO.setToolConfigInfoList(new ArrayList<>());
                        taskDetailVO.setMinStartTime(0L);
                    }

                    List<ToolConfigInfoVO> toolConfigInfoVOs = new ArrayList<>();
                    //重置工具顺序，并且对工具清单顺序也进行重排
                    taskDetailVO.setToolNames(resetToolOrderByType(taskDetailVO.getToolNames(), toolIdsOrder, taskDetailVO.getToolConfigInfoList(),
                            toolConfigInfoVOs));
                    taskDetailVO.setToolConfigInfoList(toolConfigInfoVOs);
                    return taskDetailVO;
                }).
                collect(Collectors.toList());
        //根据任务状态过滤
        if(null != taskListReqVO.getTaskStatus())
        {
            taskDetailVOS = taskDetailVOS.stream().filter(taskDetailVO -> {
                Boolean selected = false;
                switch (taskListReqVO.getTaskStatus())
                {
                    case SUCCESS:
                        if(null != taskDetailVO.getDisplayStepStatus() && null != taskDetailVO.getDisplayStep() &&
                                taskDetailVO.getDisplayStepStatus() == StepStatus.SUCC.value() &&
                                    taskDetailVO.getDisplayStep() >= Step4MutliTool.COMPLETE.value())
                        {
                            selected = true;
                        }
                        break;
                    case FAIL:
                        if(null != taskDetailVO.getDisplayStepStatus() &&
                                taskDetailVO.getDisplayStepStatus() == StepStatus.FAIL.value())
                        {
                            selected = true;
                        }
                        break;
                    case WAITING:
                        if(null == taskDetailVO.getDisplayStepStatus() ||
                                (null != taskDetailVO.getDisplayStepStatus() &&
                                taskDetailVO.getDisplayStepStatus() == StepStatus.SUCC.value() &&
                                        (null == taskDetailVO.getDisplayStep() ||
                                            taskDetailVO.getDisplayStep() == StepStatus.SUCC.value())))
                        {
                            selected = true;
                        }
                        break;
                    case ANALYSING:
                        if(null != taskDetailVO.getDisplayStepStatus() && null != taskDetailVO.getDisplayStep() &&
                                taskDetailVO.getDisplayStepStatus() != StepStatus.FAIL.value() &&
                                taskDetailVO.getDisplayStep() > Step4MutliTool.READY.value() &&
                                    taskDetailVO.getDisplayStep() < Step4MutliTool.COMPLETE.value())
                        {
                            selected = true;
                        }
                        break;
                    case DISABLED:
                        if(Status.DISABLE.value() == taskDetailVO.getStatus())
                        {
                            selected = true;
                        }
                        break;
                    default:
                        break;
                }
                return selected;
            }).collect(Collectors.toList());
        }

        return sortByDate(taskDetailVOS, taskSortType);
    }


    @Override
    public TaskListVO getTaskBaseList(String projectId, String user)
    {
        Set<TaskInfoEntity> resultSet = getQualifiedTaskList(projectId, user, null, null);
        if (CollectionUtils.isNotEmpty(resultSet))
        {
            List<TaskDetailVO> taskBaseVOList = resultSet.stream().map(taskInfoEntity ->
            {
                TaskDetailVO taskDetailVO = new TaskDetailVO();
                taskDetailVO.setTaskId(taskInfoEntity.getTaskId());
                taskDetailVO.setEntityId(taskInfoEntity.getEntityId());
                taskDetailVO.setNameCn(taskInfoEntity.getNameCn());
                taskDetailVO.setNameEn(taskInfoEntity.getNameEn());
                taskDetailVO.setStatus(taskInfoEntity.getStatus());
                taskDetailVO.setToolNames(taskInfoEntity.getToolNames());
                taskDetailVO.setCreatedDate(taskInfoEntity.getCreatedDate());
                taskDetailVO.setDisableTime(taskInfoEntity.getDisableTime());
                //设置置顶标识
                taskDetailVO.setTopFlag(-1);
                if(CollectionUtils.isNotEmpty(taskInfoEntity.getTopUser()))
                {
                    if(taskInfoEntity.getTopUser().contains(user))
                    {
                        taskDetailVO.setTopFlag(1);
                    }
                }
                return taskDetailVO;
            }).
                    collect(Collectors.toList());
            List<TaskDetailVO> enableTaskList = taskBaseVOList.stream()
                    .filter(taskDetailVO ->
                            !TaskConstants.TaskStatus.DISABLE.value().equals(taskDetailVO.getStatus()))
                    .sorted((o1, o2) -> o2.getTopFlag() - o1.getTopFlag() == 0 ? o2.getCreatedDate().compareTo(o1.getCreatedDate()) :
                        o2.getTopFlag() - o1.getTopFlag())
                    .collect(Collectors.toList());
            List<TaskDetailVO> disableTaskList = taskBaseVOList.stream()
                    .filter(taskDetailVO ->
                            TaskConstants.TaskStatus.DISABLE.value().equals(taskDetailVO.getStatus()))
                    .sorted((o1, o2) -> o2.getTopFlag() - o1.getTopFlag() == 0 ?
                            (StringUtils.isEmpty(o2.getDisableTime()) ? Long.valueOf(0) : Long.valueOf(o2.getDisableTime()))
                            .compareTo(StringUtils.isEmpty(o1.getDisableTime()) ? Long.valueOf(0) : Long.valueOf(o1.getDisableTime())) :
                            o2.getTopFlag() - o1.getTopFlag())
                    .collect(Collectors.toList());
            return new TaskListVO(enableTaskList, disableTaskList);


        }
        return null;
    }


    /**
     * 查询符合条件的任务清单
     *
     * @param projectId
     * @param user
     * @return
     */
    private Set<TaskInfoEntity> getQualifiedTaskList(String projectId, String user, Integer taskStatus, String taskSource)
    {
        log.info("begin to get task list!");
        Set<TaskInfoEntity> taskInfoEntities = taskRepository.findByProjectId(projectId);
        // 查询用户有权限的CodeCC任务
        Set<String> tasks = authExPermissionApi.queryTaskListForUser(user, projectId, Sets.newHashSet(CodeCCAuthAction.REPORT_VIEW.getActionName()));

        // 查询用户有权限的流水线
        Set<String> pipelines = authExPermissionApi.queryPipelineListForUser(user, projectId, Sets.newHashSet(PipelineAuthAction.VIEW.getActionName()));

        //查询任务清单速度优化
        Set<TaskInfoEntity> resultTasks = taskInfoEntities.stream().filter(taskInfoEntity ->
                ((CollectionUtils.isNotEmpty(taskInfoEntity.getTaskOwner()) && taskInfoEntity.getTaskOwner().contains(user) &&
                        taskInfoEntity.getStatus().equals(TaskConstants.TaskStatus.DISABLE.value()) &&
                        !(BsTaskCreateFrom.BS_PIPELINE.value().equalsIgnoreCase(taskInfoEntity.getCreateFrom()))) ||
                        (CollectionUtils.isNotEmpty(tasks) && tasks.contains(String.valueOf(taskInfoEntity.getTaskId()))) ||
                        (CollectionUtils.isNotEmpty(pipelines) && pipelines.contains(taskInfoEntity.getPipelineId()))) &&
                        //如果有过滤条件，要加过滤
                        (taskInfoEntity.getStatus().equals(taskStatus) || null == taskStatus) &&
                        ((StringUtils.isNotBlank(taskSource) && taskInfoEntity.getCreateFrom().equals(taskSource)) || StringUtils.isBlank(taskSource))
        ).collect(Collectors.toSet());

        log.info("task mongorepository finish, list length: {}", resultTasks.size());
        return resultTasks;
    }


    @Override
    public TaskBaseVO getTaskInfo()
    {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String taskId = request.getHeader(CODECC_AUTH_HEADER_DEVOPS_TASK_ID);
        String projectId = request.getHeader(CODECC_AUTH_HEADER_DEVOPS_PROJECT_ID);
        log.info("getTaskInfo: {}", taskId);
        if (!StringUtils.isNumeric(taskId))
        {
            throw new CodeCCException(CommonMessageCode.PARAMETER_IS_INVALID, new String[]{String.valueOf(taskId)}, null);
        }
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(Long.valueOf(taskId));

        if (taskEntity == null)
        {
            log.error("can not find task by taskId: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{taskId}, null);
        }

        TaskBaseVO taskBaseVO = new TaskBaseVO();
        BeanUtils.copyProperties(taskEntity, taskBaseVO);

        // 加入新告警判定配置
        if (taskEntity.getNewDefectJudge() != null)
        {
            NewDefectJudgeVO newDefectJudge = new NewDefectJudgeVO();
            BeanUtils.copyProperties(taskEntity.getNewDefectJudge(), newDefectJudge);
            taskBaseVO.setNewDefectJudge(newDefectJudge);
        }

        //添加个性化报告信息
        NotifyCustomVO notifyCustomVO = new NotifyCustomVO();
        NotifyCustomEntity notifyCustomEntity = taskEntity.getNotifyCustomInfo();
        if (null != notifyCustomEntity)
        {
            BeanUtils.copyProperties(notifyCustomEntity, notifyCustomVO);
        }
        taskBaseVO.setNotifyCustomInfo(notifyCustomVO);

        // 给工具分类及排序，并加入规则集
        sortedToolList(taskBaseVO, taskEntity.getToolConfigInfoList());

        //获取规则和规则集数量
        CodeCCResult<TaskBaseVO> checkerCountVO = client.get(ServiceCheckerSetRestResource.class).getCheckerAndCheckerSetCount(Long.valueOf(taskId), projectId);
        if(checkerCountVO.isOk() && null != checkerCountVO.getData())
        {
            taskBaseVO.setCheckerSetName(checkerCountVO.getData().getCheckerSetName());
            taskBaseVO.setCheckerCount(checkerCountVO.getData().getCheckerCount());
        }

        return taskBaseVO;
    }

    @Override
    public TaskDetailVO getTaskInfoById(Long taskId)
    {
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(taskId);
        if (taskEntity == null)
        {
            log.error("can not find task by taskId: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        try
        {
            String taskInfoStr = objectMapper.writeValueAsString(taskEntity);
            return objectMapper.readValue(taskInfoStr, new TypeReference<TaskDetailVO>()
            {
            });
        }
        catch (IOException e)
        {
            String message = "string conversion TaskDetailVO error";
            log.error(message);
            throw new StreamException(message);
        }
    }

    @Override
    public TaskDetailVO getTaskInfoWithoutToolsByTaskId(Long taskId)
    {
        TaskInfoEntity taskEntity = taskRepository.findTaskInfoWithoutToolsByTaskId(taskId);
        if (taskEntity == null)
        {
            log.error("can not find task by taskId: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        TaskDetailVO taskDetailVO = new TaskDetailVO();
        BeanUtils.copyProperties(taskEntity, taskDetailVO);
        return taskDetailVO;
    }

    @Override
    public TaskDetailVO getTaskInfoByStreamName(String streamName)
    {
        TaskInfoEntity taskEntity = taskRepository.findByNameEn(streamName);

        if (taskEntity == null)
        {
            log.error("can not find task by streamName: {}", streamName);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{streamName}, null);
        }

        TaskDetailVO taskDetailVO = new TaskDetailVO();
        BeanUtils.copyProperties(taskEntity, taskDetailVO);

        // 加入工具列表
        List<ToolConfigInfoEntity> toolEntityList = taskEntity.getToolConfigInfoList();
        if (CollectionUtils.isNotEmpty(toolEntityList))
        {
            Set<String> toolSet = new HashSet<>();
            taskDetailVO.setToolSet(toolSet);

            for (ToolConfigInfoEntity toolEntity : toolEntityList)
            {
                if (TaskConstants.FOLLOW_STATUS.WITHDRAW.value() != toolEntity.getFollowStatus())
                {
                    toolSet.add(toolEntity.getToolName());
                }
            }
        }


        taskDetailVO.setToolConfigInfoList(toolEntityList.stream().map(toolConfigInfoEntity -> {
            ToolConfigInfoVO toolConfigInfoVO = new ToolConfigInfoVO();
            BeanUtils.copyProperties(toolConfigInfoEntity, toolConfigInfoVO, "checkerSet");
            return toolConfigInfoVO;
        }).collect(Collectors.toList()));

        // 加入通知定制配置
        if (taskEntity.getNotifyCustomInfo() != null)
        {
            NotifyCustomVO notifyCustomVO = new NotifyCustomVO();
            BeanUtils.copyProperties(taskEntity.getNotifyCustomInfo(), notifyCustomVO);
            taskDetailVO.setNotifyCustomInfo(notifyCustomVO);
        }

        // 加入新、历史告警判定
        if (taskEntity.getNewDefectJudge() != null)
        {
            NewDefectJudgeVO newDefectJudgeVO = new NewDefectJudgeVO();
            BeanUtils.copyProperties(taskEntity.getNewDefectJudge(), newDefectJudgeVO);
            taskDetailVO.setNewDefectJudge(newDefectJudgeVO);
        }

        return taskDetailVO;
    }


    private Boolean taskDetailDisplayInfo(ToolConfigInfoEntity toolConfigInfoEntity, TaskDetailVO taskDetailVO, String displayName)
    {
        Integer displayStepStatus = 0;
        //检测到有任务运行中（非成功状态）
        Boolean processFlag = false;
        //更新工具显示状态
        //如果有失败的工具，则显示失败的状态
        if (toolConfigInfoEntity.getStepStatus() == StepStatus.FAIL.value())
        {
            displayStepStatus = StepStatus.FAIL.value();
            taskDetailVO.setDisplayStepStatus(displayStepStatus);
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果没找到失败的工具，有分析中的工具，则显示分析中
        else if (toolConfigInfoEntity.getStepStatus() == StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() < Step4MutliTool.COMPLETE.value() &&
                toolConfigInfoEntity.getCurStep() > Step4MutliTool.READY.value() &&
                displayStepStatus != StepStatus.FAIL.value())
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果没找到失败的工具，有准备的工具，则显示准备
        else if (toolConfigInfoEntity.getStepStatus() == StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() == Step4MutliTool.READY.value() &&
                displayStepStatus != StepStatus.FAIL.value())
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
            processFlag = true;
        }
        //如果还没找到其他状态，则显示成功
        else if (toolConfigInfoEntity.getStepStatus() == StepStatus.SUCC.value() &&
                toolConfigInfoEntity.getCurStep() >= Step4MutliTool.COMPLETE.value() &&
                StringUtils.isBlank(taskDetailVO.getDisplayToolName()))
        {
            taskDetailVO.setDisplayToolName(toolConfigInfoEntity.getToolName());
            taskDetailVO.setDisplayStep(toolConfigInfoEntity.getCurStep());
            taskDetailVO.setDisplayName(displayName);
        }
        return processFlag;

    }

    /**
     * 获取任务接入的工具列表
     *
     * @param taskId
     * @return
     */
    @Override
    public TaskBaseVO getTaskToolList(long taskId)
    {
        List<ToolConfigInfoEntity> toolEntityList = toolRepository.findByTaskId(Long.valueOf(taskId));

        TaskBaseVO taskBaseVO = new TaskBaseVO();

        // 给工具分类及排序
        sortedToolList(taskBaseVO, toolEntityList);

        return taskBaseVO;
    }

    /**
     * 修改任务
     *
     * @param taskUpdateVO
     * @param userName
     * @return
     */
    @Override
    @OperationHistory(funcId = FUNC_TASK_INFO, operType = MODIFY_INFO)
    public Boolean updateTask(TaskUpdateVO taskUpdateVO, Long taskId, String userName)
    {
        // 检查参数
        if (!checkParam(taskUpdateVO))
        {
            return false;
        }

        // 任务是否注册过
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        if (Objects.isNull(taskInfoEntity))
        {
            log.error("can not find task info");
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        // 修改任务信息
        taskDao.updateTask(taskUpdateVO.getTaskId(), taskUpdateVO.getCodeLang(), taskUpdateVO.getNameCn(), taskUpdateVO.getTaskOwner(),
                taskUpdateVO.getTaskMember(), taskUpdateVO.getDisableTime(), taskUpdateVO.getStatus(),
                userName);

        // 判断是否存在流水线，如果是则将任务语言同步到蓝盾流水线编排
        if (StringUtils.isNotBlank(taskInfoEntity.getPipelineId()))
        {
            if (taskInfoEntity.getCreateFrom().equals(ComConstants.BsTaskCreateFrom.BS_CODECC.value()))
            {
                // codecc服务创建的任务
                pipelineService.updateBsPipelineLang(taskInfoEntity, userName);
            }

            // 流水线任务修改语言和任务名不再同步到流水线Model，只允许从流水线插件上修改
            /*else if (taskInfoEntity.getCreateFrom().equals(ComConstants.BsTaskCreateFrom.BS_PIPELINE.value()))
            {
                // 流水线添加codecc原子创建的任务, 存在语言
                pipelineService.updateBsPipelineLangBSChannelCode(taskInfoEntity, userName);
            }*/
        }

        //根据语言解绑规则集
        client.get(ServiceCheckerSetRestResource.class).updateCheckerSetAndTaskRelation(taskId, taskUpdateVO.getCodeLang(), userName);

        return true;
    }

    /**
     * 修改任务基本信息 - 内部服务间调用
     *
     * @param taskUpdateVO
     * @param userName
     * @return
     */
    @Override
    public Boolean updateTaskByServer(TaskUpdateVO taskUpdateVO, String userName)
    {
        return taskDao.updateTask(taskUpdateVO.getTaskId(), taskUpdateVO.getCodeLang(), taskUpdateVO.getNameCn(), taskUpdateVO.getTaskOwner(),
                taskUpdateVO.getTaskMember(), taskUpdateVO.getDisableTime(), taskUpdateVO.getStatus(),
                userName);
    }

    @Override
    public TaskOverviewVO getTaskOverview(Long taskId)
    {
        TaskInfoEntity taskEntity = taskRepository.findToolListByTaskId(taskId);
        if (taskEntity == null)
        {
            log.error("can not find task by taskId: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        TaskOverviewVO taskOverviewVO = new TaskOverviewVO();
        taskOverviewVO.setTaskId(taskId);
        List<ToolConfigInfoEntity> toolConfigInfoList = taskEntity.getToolConfigInfoList();
        if (CollectionUtils.isNotEmpty(toolConfigInfoList))
        {
            List<TaskOverviewVO.LastAnalysis> toolLastAnalysisList = new ArrayList<>();
            Map<String, TaskOverviewVO.LastAnalysis> toolLastAnalysisMap = new HashMap<>();
            for (ToolConfigInfoEntity tool : toolConfigInfoList)
            {
                int followStatus = tool.getFollowStatus();
                //todo 手动屏蔽CLOC
                if ("CLOC".equalsIgnoreCase(tool.getToolName()))
                {
                    continue;
                }
                if (followStatus != TaskConstants.FOLLOW_STATUS.WITHDRAW.value())
                {
                    TaskOverviewVO.LastAnalysis lastAnalysis = new TaskOverviewVO.LastAnalysis();
                    String toolName = tool.getToolName();
                    lastAnalysis.setToolName(toolName);
                    lastAnalysis.setCurStep(tool.getCurStep());
                    lastAnalysis.setStepStatus(tool.getStepStatus());
                    toolLastAnalysisMap.put(toolName, lastAnalysis);
                    toolLastAnalysisList.add(lastAnalysis);
                }
            }

            // 调用defect模块的接口获取工具的最近一次分析结果
            GetLastAnalysisResultsVO getLastAnalysisResultsVO = new GetLastAnalysisResultsVO();
            getLastAnalysisResultsVO.setTaskId(taskId);
            getLastAnalysisResultsVO.setToolSet(toolLastAnalysisMap.keySet());
            CodeCCResult<List<ToolLastAnalysisResultVO>> codeCCResult = client.get(ServiceTaskLogRestResource.class).getLastAnalysisResults(getLastAnalysisResultsVO);
            if (codeCCResult.isNotOk() || null == codeCCResult.getData())
            {
                log.error("get last analysis results fail! taskId is: {}, msg: {}", taskId, codeCCResult.getMessage());
                throw new CodeCCException(CommonMessageCode.INTERNAL_SYSTEM_FAIL);
            }
            List<ToolLastAnalysisResultVO> lastAnalysisResultVOs = codeCCResult.getData();

            if (CollectionUtils.isNotEmpty(lastAnalysisResultVOs))
            {
                for (ToolLastAnalysisResultVO toolLastAnalysisResultVO : lastAnalysisResultVOs)
                {
                    TaskOverviewVO.LastAnalysis lastAnalysis = toolLastAnalysisMap.get(toolLastAnalysisResultVO.getToolName());
                    lastAnalysis.setLastAnalysisResult(toolLastAnalysisResultVO.getLastAnalysisResultVO());
                    long elapseTime = toolLastAnalysisResultVO.getElapseTime();
                    long endTime = toolLastAnalysisResultVO.getEndTime();
                    long startTime = toolLastAnalysisResultVO.getStartTime();
                    long lastAnalysisTime = endTime;
                    if (elapseTime == 0 && endTime != 0)
                    {
                        elapseTime = endTime - startTime;
                    }

                    if (endTime == 0)
                    {
                        lastAnalysisTime = startTime;
                    }
                    lastAnalysis.setElapseTime(elapseTime);
                    lastAnalysis.setLastAnalysisTime(lastAnalysisTime);
                    lastAnalysis.setBuildId(toolLastAnalysisResultVO.getBuildId());
                    lastAnalysis.setBuildNum(toolLastAnalysisResultVO.getBuildNum());
                }
            }
            String orderToolIds = commonDao.getToolOrder();
            List<String> toolOrderList = Arrays.asList(orderToolIds.split(","));

            toolLastAnalysisList.sort(Comparator.comparingInt(o -> toolOrderList.indexOf(o.getToolName()))
            );

            taskOverviewVO.setLastAnalysisResultList(toolLastAnalysisList);
        }
        return taskOverviewVO;
    }


    /**
     * 开启任务
     *
     * @param taskId
     * @return
     */
    @Override
    @OperationHistory(funcId = FUNC_TASK_SWITCH, operType = ENABLE_ACTION)
    public Boolean startTask(Long taskId, String userName)
    {
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(taskId);
        if (Objects.isNull(taskEntity))
        {
            log.error("taskInfo not exists! task id is: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }
        List<String> taskMemberList = taskEntity.getTaskMember();
        List<String> taskOwnerList = taskEntity.getTaskOwner();
        Boolean taskMemberPermission = CollectionUtils.isEmpty(taskMemberList) || !taskMemberList.contains(userName);
        Boolean taskOwnerPermission = CollectionUtils.isEmpty(taskOwnerList) || !taskOwnerList.contains(userName);
        if (taskMemberPermission && taskOwnerPermission)
        {
            log.error("current user has no permission to the task");
            throw new CodeCCException(CommonMessageCode.PERMISSION_DENIED, new String[]{userName}, null);
        }


        if (CollectionUtils.isNotEmpty(taskEntity.getExecuteDate()) && StringUtils.isNotBlank(taskEntity.getExecuteTime()))
        {
            log.error("The task is already open and cannot be repeated.");
            throw new CodeCCException(TaskMessageCode.TASK_HAS_START);
        }

        // 如果是蓝盾项目，要开启流水线定时触发任务
        if (StringUtils.isNotBlank(taskEntity.getProjectId()))
        {
            // 启动时，把原先的定时任务恢复
            DisableTaskEntity lastDisableTaskInfo = taskEntity.getLastDisableTaskInfo();
            if (Objects.isNull(lastDisableTaskInfo))
            {
                log.error("pipeline execution timing is empty.");
//                throw new CodeCCException(TaskMessageCode.PIPELINE_EXECUTION_TIME_EMPTY);
            }
            else
            {
                String lastExecuteTime = lastDisableTaskInfo.getLastExecuteTime();
                List<String> lastExecuteDate = lastDisableTaskInfo.getLastExecuteDate();

                // 开启定时执行的日期时间
                taskEntity.setExecuteTime(lastExecuteTime);
                taskEntity.setExecuteDate(lastExecuteDate);
                // 删除DB保存的执行时间
                taskEntity.setLastDisableTaskInfo(null);
                pipelineService.modifyCodeCCTiming(taskEntity, lastExecuteDate, lastExecuteTime, userName);
            }
        }

        taskEntity.setDisableTime("");
        taskEntity.setDisableReason("");
        taskEntity.setStatus(TaskConstants.TaskStatus.ENABLE.value());

        //在权限中心重新注册任务
        authExRegisterApi.registerCodeCCTask(userName, String.valueOf(taskId), taskEntity.getNameEn(), taskEntity.getProjectId());

        //恢复日报
        if(null != taskEntity.getNotifyCustomInfo() && StringUtils.isNotBlank(taskEntity.getNotifyCustomInfo().getReportJobName()))
        {
            JobExternalDto jobExternalDto = new JobExternalDto(
                    taskEntity.getNotifyCustomInfo().getReportJobName(),
                    "",
                    "",
                    "",
                    new HashMap<>(),
                    OperationType.RESUME
            );
            rabbitTemplate.convertAndSend(EXCHANGE_EXTERNAL_JOB, "", jobExternalDto);
        }

        return taskDao.updateEntity(taskEntity, userName);
    }


    /**
     * 停用任务
     *
     * @param taskId
     * @param disabledReason
     * @param userName
     * @return
     */
    @Override
    @OperationHistory(funcId = FUNC_TASK_SWITCH, operType = DISABLE_ACTION)
    public Boolean stopTask(Long taskId, String disabledReason, String userName)
    {
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(taskId);
        if (Objects.isNull(taskEntity))
        {
            log.error("taskInfo not exists! task id is: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }
        //判断是否有权限
        List<String> taskMemberList = taskEntity.getTaskMember();
        List<String> taskOwnerList = taskEntity.getTaskOwner();
        Boolean taskMemberPermission = CollectionUtils.isEmpty(taskMemberList) || !taskMemberList.contains(userName);
        Boolean taskOwnerPermission = CollectionUtils.isEmpty(taskOwnerList) || !taskOwnerList.contains(userName);
        if (taskMemberPermission && taskOwnerPermission)
        {
            log.error("current user has no permission to the task");
            throw new CodeCCException(CommonMessageCode.PERMISSION_DENIED, new String[]{userName}, new Exception());
        }

        if (StringUtils.isNotBlank(taskEntity.getDisableTime()))
        {
            log.error("The task is already close and cannot be repeated.");
            throw new CodeCCException(TaskMessageCode.TASK_HAS_CLOSE);
        }

        // 如果是蓝盾项目，并且是服务创建的，要停止流水线定时触发任务
        if (StringUtils.isNotBlank(taskEntity.getProjectId()) && BsTaskCreateFrom.BS_CODECC.value().equalsIgnoreCase(taskEntity.getCreateFrom()))
        {
            String executeTime = taskEntity.getExecuteTime();
            List<String> executeDate = taskEntity.getExecuteDate();

            if(CollectionUtils.isEmpty(executeDate))
            {
                log.error("pipeline execute date is empty. task id : {}", taskId);
                executeDate = Collections.emptyList();
            }

            if(StringUtils.isBlank(executeTime))
            {
                log.error("pipeline execute time is empty. task id : {}", taskId);
                executeTime = "";
            }

            // 调用蓝盾API 删除定时构建原子
            pipelineService.deleteCodeCCTiming(userName, taskEntity);

            // 存储启用日期时间到DisableTaskEntity
            DisableTaskEntity lastDisableTaskInfo = taskEntity.getLastDisableTaskInfo();
            if (Objects.isNull(lastDisableTaskInfo))
            {
                lastDisableTaskInfo = new DisableTaskEntity();
            }

            lastDisableTaskInfo.setLastExecuteTime(executeTime);
            lastDisableTaskInfo.setLastExecuteDate(executeDate);
            taskEntity.setLastDisableTaskInfo(lastDisableTaskInfo);
        }

        //要将权限中心的任务成员，任务管理员同步到task表下面，便于后续启用时再进行注册
        TaskMemberVO taskMemberVO = getTaskUsers(taskId, taskEntity.getProjectId());
        taskEntity.setTaskMember(taskMemberVO.getTaskMember());
        taskEntity.setTaskOwner(taskMemberVO.getTaskOwner());
        taskEntity.setTaskViewer(taskMemberVO.getTaskViewer());

        //在权限中心中删除相应的资源
        if (BsTaskCreateFrom.BS_CODECC.value().equalsIgnoreCase(taskEntity.getCreateFrom()))
        {
            try
            {
                authExRegisterApi.deleteCodeCCTask(String.valueOf(taskId), taskEntity.getProjectId());
            }
            catch (UnauthorizedException e)
            {
                log.error("delete iam resource fail! error message: {}", e.getMessage());
                throw new CodeCCException(TaskMessageCode.CLOSE_TASK_FAIL);
            }
        }

        log.info("stopping task: delete pipeline scheduled atom and auth center resource success! project id: {}",
                taskEntity.getProjectId());

        taskEntity.setExecuteDate(new ArrayList<>());
        taskEntity.setExecuteTime("");
        taskEntity.setDisableTime(String.valueOf(System.currentTimeMillis()));
        taskEntity.setDisableReason(disabledReason);
        taskEntity.setStatus(TaskConstants.TaskStatus.DISABLE.value());

        //停止日报
        if(null != taskEntity.getNotifyCustomInfo() && StringUtils.isNotBlank(taskEntity.getNotifyCustomInfo().getReportJobName()))
        {
            JobExternalDto jobExternalDto = new JobExternalDto(
                    taskEntity.getNotifyCustomInfo().getReportJobName(),
                    "",
                    "",
                    "",
                    new HashMap<>(),
                    OperationType.PARSE
            );
            rabbitTemplate.convertAndSend(EXCHANGE_EXTERNAL_JOB, "", jobExternalDto);
        }



        return taskDao.updateEntity(taskEntity, userName);
    }


    /**
     * 获取代码库配置信息
     *
     * @param taskId
     * @return
     */
    @Override
    public TaskCodeLibraryVO getCodeLibrary(Long taskId)
    {
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(taskId);
        if (Objects.isNull(taskEntity))
        {
            log.error("taskInfo not exists! task id is: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        // 获取所有工具的基础信息
        Map<String, ToolMetaBaseVO> toolMetaMap = toolMetaCache.getToolMetaListFromCache(Boolean.FALSE, Boolean.TRUE);

        // 获取排序好的所有工具
        String[] toolIdArr = Optional.ofNullable(taskEntity.getToolNames())
                .map(tool -> tool.split(ComConstants.STRING_SPLIT))
                .orElse(new String[]{});

        // 获取工具配置Map
        Map<String, JSONObject> chooseJsonMap = getToolConfigInfoMap(taskEntity);

        Map<String, GlobalMessage> tipsMessage = globalMessageUtil.getGlobalMessageMap(GLOBAL_TOOL_PARAMS_TIPS);
        Map<String, GlobalMessage> labelNameMessage = globalMessageUtil.getGlobalMessageMap(GLOBAL_TOOL_PARAMS_LABEL_NAME);
        List<ToolConfigParamJsonVO> paramJsonList = new ArrayList<>();
        for (String toolName : toolIdArr)
        {
            // 工具被禁用则不显示
            if (!chooseJsonMap.keySet().contains(toolName))
            {
                continue;
            }

            // 获取工具对应的基本数据
            ToolMetaBaseVO toolMetaBaseVO = toolMetaMap.get(toolName);

            if (Objects.nonNull(toolMetaBaseVO))
            {
                String params = toolMetaBaseVO.getParams();
                if (StringUtils.isNotBlank(params) && !ComConstants.STRING_NULL_ARRAY.equals(params))
                {
                    JSONObject chooseJson = chooseJsonMap.get(toolName);
                    List<Map<String, Object>> arrays = JsonUtil.INSTANCE.to(params);
                    for (Map<String, Object> array : arrays)
                    {
                        ToolConfigParamJsonVO toolConfig = JsonUtil.INSTANCE.mapTo(array, ToolConfigParamJsonVO.class);
                        String toolChooseValue = Objects.isNull(chooseJson) ?
                                toolConfig.getVarDefault() : StringUtils.isBlank((String) chooseJson.get(toolConfig.getVarName())) ?
                                toolConfig.getVarDefault() : (String) chooseJson.get(toolConfig.getVarName());


                        // 工具参数标签[ labelName ]国际化
                        GlobalMessage labelGlobalMessage = labelNameMessage.get(String.format("%s:%s", toolName, toolConfig.getVarName()));
                        if (Objects.nonNull(labelGlobalMessage))
                        {
                            String globalLabelName = globalMessageUtil.getMessageByLocale(labelGlobalMessage);
                            toolConfig.setLabelName(globalLabelName);
                        }

                        // 工具参数提示[ tips ]国际化
                        GlobalMessage tipGlobalMessage = tipsMessage.get(String.format("%s:%s", toolName, toolConfig.getVarName()));
                        if (Objects.nonNull(tipGlobalMessage))
                        {
                            String globalTips = globalMessageUtil.getMessageByLocale(tipGlobalMessage);
                            toolConfig.setVarTips(globalTips);
                        }

                        toolConfig.setTaskId(taskId);
                        toolConfig.setToolName(toolMetaBaseVO.getName());
                        toolConfig.setChooseValue(toolChooseValue);
                        paramJsonList.add(toolConfig);
                    }
                }
            }
        }

        TaskCodeLibraryVO taskCodeLibrary = new TaskCodeLibraryVO();
        BeanUtils.copyProperties(taskEntity, taskCodeLibrary);
        taskCodeLibrary.setToolConfigList(paramJsonList);
        taskCodeLibrary.setRepoHashId(taskEntity.getRepoHashId());

        if (StringUtils.isBlank(taskCodeLibrary.getBranch()))
        {
            taskCodeLibrary.setBranch("master");
        }

        return taskCodeLibrary;
    }


    @Override
    public Boolean checkTaskExists(long taskId)
    {
        return taskRepository.findByTaskId(taskId) != null;
    }


    /**
     * 更新代码库信息
     *
     * @param taskId
     * @param taskDetailVO
     * @return
     */
    @Override
    @OperationHistory(funcId = FUNC_CODE_REPOSITORY, operType = MODIFY_INFO)
    public Boolean updateCodeLibrary(Long taskId, String userName, TaskDetailVO taskDetailVO)
    {
        TaskInfoEntity taskEntity = taskRepository.findByTaskId(taskId);
        if (Objects.isNull(taskEntity))
        {
            log.error("taskInfo not exists! task id is: {}", taskId);
            throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS, new String[]{String.valueOf(taskId)}, null);
        }

        // 更新工具配置信息
        updateToolConfigInfoEntity(taskDetailVO, taskEntity, userName);

        // 代码仓库是否修改
        boolean repoIdUpdated = false;
        if (StringUtils.isNotEmpty(taskDetailVO.getRepoHashId()))
        {
            if (!taskDetailVO.getRepoHashId().equals(taskEntity.getRepoHashId()))
            {
                repoIdUpdated = true;
            }
        }
        taskEntity.setRepoHashId(taskDetailVO.getRepoHashId());
        taskEntity.setBranch(taskDetailVO.getBranch());
        taskEntity.setScmType(taskDetailVO.getScmType());
        taskEntity.setAliasName(taskDetailVO.getAliasName());
        taskEntity.setOsType(StringUtils.isNotEmpty(taskDetailVO.getOsType()) ? taskDetailVO.getOsType() : taskEntity.getOsType());
        taskEntity.setBuildEnv(MapUtils.isNotEmpty(taskDetailVO.getBuildEnv()) ? taskDetailVO.getBuildEnv() : taskEntity.getBuildEnv());
        taskEntity.setProjectBuildType((StringUtils.isNotEmpty(taskDetailVO.getProjectBuildType()) ? taskDetailVO.getProjectBuildType() : taskEntity.getProjectBuildType()));
        taskEntity.setProjectBuildCommand(StringUtils.isNotEmpty(taskDetailVO.getProjectBuildCommand()) ? taskDetailVO.getProjectBuildCommand() : taskEntity.getProjectBuildCommand());

        BatchRegisterVO registerVO = new BatchRegisterVO();
        registerVO.setRepoHashId(taskEntity.getRepoHashId());
        registerVO.setBranch(taskEntity.getBranch());
        registerVO.setScmType(taskEntity.getScmType());
        registerVO.setOsType(taskDetailVO.getOsType());
        registerVO.setBuildEnv(taskDetailVO.getBuildEnv());
        registerVO.setProjectBuildType(taskDetailVO.getProjectBuildType());
        registerVO.setProjectBuildCommand(taskDetailVO.getProjectBuildCommand());
        //更新流水线设置
        pipelineService.updatePipelineTools(userName, taskId, Collections.EMPTY_LIST,
                taskEntity, PipelineToolUpdateType.GET, registerVO, getRelPath(taskDetailVO.getDevopsToolParams()));

        // 设置强制全量扫描标志
        if (repoIdUpdated)
        {
            setForceFullScan(taskEntity);
        }

        return taskDao.updateEntity(taskEntity, userName);
    }


    private String getRelPath(List<ToolConfigParamJsonVO> toolConfigList)
    {
        if (CollectionUtils.isNotEmpty(toolConfigList))
        {
            for (ToolConfigParamJsonVO toolConfigParamJsonVO : toolConfigList)
            {
                if (Tool.GOML.name().equalsIgnoreCase(toolConfigParamJsonVO.getToolName()))
                {
                    if (ComConstants.PARAMJSON_KEY_REL_PATH.equalsIgnoreCase(toolConfigParamJsonVO.getVarName()))
                    {
                        return toolConfigParamJsonVO.getChooseValue();
                    }
                }
            }
        }
        return "";
    }


    /**
     * 获取任务成员及管理员清单
     *
     * @param taskId
     * @param projectId
     * @return
     */
    @Override
    public TaskMemberVO getTaskUsers(long taskId, String projectId)
    {

        TaskMemberVO taskMemberVO = new TaskMemberVO();
        String taskCreateFrom = authExPermissionApi.getTaskCreateFrom(taskId);
        if (ComConstants.BsTaskCreateFrom.BS_CODECC.value().equals(taskCreateFrom))
        {
            // 获取各角色对应用户列表
            List<String> taskMembers = authExPermissionApi.queryTaskUserListForAction(String.valueOf(taskId), projectId,
                    PermissionUtil.INSTANCE.getCodeCCPermissionsFromActions(AuthRole.TASK_MEMBER.getCodeccActions()));
            List<String> taskOwners = authExPermissionApi.queryTaskUserListForAction(String.valueOf(taskId), projectId,
                    PermissionUtil.INSTANCE.getCodeCCPermissionsFromActions(AuthRole.TASK_OWNER.getCodeccActions()));
            List<String> taskViews = authExPermissionApi.queryTaskUserListForAction(String.valueOf(taskId), projectId,
                    PermissionUtil.INSTANCE.getCodeCCPermissionsFromActions(AuthRole.TASK_VIEWER.getCodeccActions()));
            taskMemberVO.setTaskMember(taskMembers);
            taskMemberVO.setTaskOwner(taskOwners);
            taskMemberVO.setTaskViewer(taskViews);
        }

        return taskMemberVO;
    }


    @Override
    @OperationHistory(funcId = FUNC_TRIGGER_ANALYSIS, operType = TRIGGER_ANALYSIS)
    public Boolean manualExecuteTask(long taskId, String isFirstTrigger, String userName)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        List<ToolConfigInfoEntity> toolConfigInfoEntityList = taskInfoEntity.getToolConfigInfoList();
        if (CollectionUtils.isEmpty(toolConfigInfoEntityList))
        {
            log.info("tool list is empty! task id: {}", taskId);
            return false;
        }

        Set<String> toolSet = toolConfigInfoEntityList.stream().filter(toolConfigInfoEntity ->
                FOLLOW_STATUS.WITHDRAW.value() != toolConfigInfoEntity.getFollowStatus()
        ).map(ToolConfigInfoEntity::getToolName
        ).collect(Collectors.toSet());


        if (CollectionUtils.isNotEmpty(toolSet))
        {
            // 支持并发后不再停用正在运行的流水线
            //停止原有正在运行的流水线
            /*Result<Boolean> stopResult = client.get(ServiceTaskLogRestResource.class).stopRunningTask(taskInfoEntity.getPipelineId(), taskInfoEntity.getNameEn(),
                    toolSet, taskInfoEntity.getProjectId(), taskId, userName);
            if (stopResult.isNotOk() || null == stopResult.getData() || !stopResult.getData())
            {
                log.error("stop running pipeline fail! task id: {}", taskId);
                throw new CodeCCException(CommonMessageCode.INTERNAL_SYSTEM_FAIL);
            }*/

            // 启动流水线
            String buildId = pipelineService.startPipeline(taskInfoEntity.getPipelineId(), taskInfoEntity.getProjectId(),
                    taskInfoEntity.getNameEn(), taskInfoEntity.getCreateFrom(), new ArrayList<>(toolSet), userName);
            //更新任务状态
            toolSet.forEach(tool ->
                    pipelineService.updateTaskInitStep(isFirstTrigger, taskInfoEntity, buildId, tool, userName)
            );

            log.info("start pipeline and send delay message");
            rabbitTemplate.convertAndSend(EXCHANGE_EXPIRED_TASK_STATUS, ROUTE_EXPIRED_TASK_STATUS, new ScanTaskTriggerDTO(taskId, buildId), message ->
            {
                //todo 配置在配置文件里
                message.getMessageProperties().setDelay(15 * 60 * 60 * 1000);
                return message;
            });
        }
        return true;
    }

    @Override
    public Boolean sendStartTaskSignal(Long taskId, String buildId)
    {
        //todo 后续和流水线对齐
        rabbitTemplate.convertAndSend(EXCHANGE_EXPIRED_TASK_STATUS, ROUTE_EXPIRED_TASK_STATUS, new ScanTaskTriggerDTO(taskId, buildId), message ->
        {
            message.getMessageProperties().setDelay(24 * 60 * 60 * 1000);
            return message;
        });
        return true;
    }


    /**
     * 通过流水线ID获取任务信息
     *
     * @param pipelineId
     * @param user
     * @return
     */
    @Override
    public PipelineTaskVO getTaskInfoByPipelineId(String pipelineId, String user)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByPipelineId(pipelineId);
        if (taskInfoEntity == null)
        {
            String errMsg = String.format("can not find task by pipeline id: {}", pipelineId);
            log.error(errMsg);
            throw new CodeCCException(CommonMessageCode.PARAMETER_IS_INVALID, new String[]{"pipeline id"}, null);
        }
        PipelineTaskVO taskDetailVO = new PipelineTaskVO();
        taskDetailVO.setProjectId(taskInfoEntity.getProjectId());
        taskDetailVO.setTaskId(taskInfoEntity.getTaskId());
        taskDetailVO.setTools(Lists.newArrayList());

        List<String> openTools = Lists.newArrayList();
        if (Objects.nonNull(taskInfoEntity))
        {
            for (ToolConfigInfoEntity toolConfigInfoEntity : taskInfoEntity.getToolConfigInfoList())
            {
                if (TaskConstants.FOLLOW_STATUS.WITHDRAW.value() != toolConfigInfoEntity.getFollowStatus())
                {
                    openTools.add(toolConfigInfoEntity.getToolName());
                    PipelineToolVO pipelineToolVO = new PipelineToolVO();
                    pipelineToolVO.setToolName(toolConfigInfoEntity.getToolName());
                    if (toolConfigInfoEntity.getCheckerSet() != null)
                    {
                        CheckerSetVO checkerSetVO = new CheckerSetVO();
                        BeanUtils.copyProperties(toolConfigInfoEntity.getCheckerSet(), checkerSetVO);
                        pipelineToolVO.setCheckerSetInUse(checkerSetVO);
                    }
                    if (StringUtils.isNotEmpty(toolConfigInfoEntity.getParamJson()))
                    {
                        pipelineToolVO.setParams(getParams(toolConfigInfoEntity.getParamJson()));
                    }
                    taskDetailVO.getTools().add(pipelineToolVO);
                }
            }
        }

//        if (StringUtils.isNotEmpty(user) && CollectionUtils.isNotEmpty(openTools))
//        {
//            Map<String, DividedCheckerSetsVO> checkerSetsMap = Maps.newHashMap();
//            if (StringUtils.isNotEmpty(user))
//            {
//                Result<UserCheckerSetsVO> checkerSetsResult = client.get(ServiceCheckerRestResource.class).getCheckerSets(taskInfoEntity.getTaskId(),
//                        new GetCheckerSetsReqVO(openTools), user, taskInfoEntity.getProjectId());
//                if (checkerSetsResult.isNotOk() || null == checkerSetsResult.getData() || CollectionUtils.isEmpty(checkerSetsResult.getData().getCheckerSets()))
//                {
//                    log.error("get checker sets fail! pipeline id: {}, task id: {}, user: {}", pipelineId, taskInfoEntity.getTaskId(), user);
//                    throw new CodeCCException(CommonMessageCode.INTERNAL_SYSTEM_FAIL);
//                }
//                for (DividedCheckerSetsVO checkerSets : checkerSetsResult.getData().getCheckerSets())
//                {
//                    checkerSetsMap.put(checkerSets.getToolName(), checkerSets);
//                }
//            }
//            for (PipelineToolVO pipelineToolVO : taskDetailVO.getTools())
//            {
//                if (checkerSetsMap.get(pipelineToolVO.getToolName()) != null)
//                {
//                    pipelineToolVO.setToolCheckerSets(checkerSetsMap.get(pipelineToolVO.getToolName()));
//                }
//            }
//        }

        // 加入语言的显示名称
        List<ProjectLanguage> codeLanguages = pipelineService.localConvertDevopsCodeLang(taskInfoEntity.getCodeLang());
        taskDetailVO.setCodeLanguages(codeLanguages);

        return taskDetailVO;
    }

    @Override
    public TaskStatusVO getTaskStatus(Long taskId)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        if (null == taskInfoEntity)
        {
            return null;
        }
        return new TaskStatusVO(taskInfoEntity.getStatus(), taskInfoEntity.getGongfengProjectId());
    }

    /**
     * 获取所有的基础工具信息
     *
     * @return
     */
    @Override
    public Map<String, ToolMetaBaseVO> getToolMetaListFromCache()
    {
        return toolMetaCache.getToolMetaListFromCache(Boolean.FALSE, Boolean.FALSE);
    }

    @Override
    public TaskInfoEntity getTaskById(Long taskId)
    {
        return taskRepository.findByTaskId(taskId);
    }

    @Override
    public Boolean saveTaskInfo(TaskInfoEntity taskInfoEntity)
    {
        taskRepository.save(taskInfoEntity);
        return true;
    }

    @Override
    public List<TaskBaseVO> getTasksByBgId(Integer bgId)
    {
        List<TaskInfoEntity> taskInfoEntityList = taskRepository.findByBgId(bgId);
        if (CollectionUtils.isNotEmpty(taskInfoEntityList))
        {
            return taskInfoEntityList.stream().map(taskInfoEntity ->
            {
                TaskBaseVO taskBaseVO = new TaskBaseVO();
                BeanUtils.copyProperties(taskInfoEntity, taskBaseVO,
                        "taskOwner", "executeDate", "enableToolList", "disableToolList");
                return taskBaseVO;
            }).collect(Collectors.toList());
        }
        else
        {
            return new ArrayList<>();
        }
    }

    @Override
    public List<TaskBaseVO> getTasksByIds(List<Long> taskIds)
    {
        List<TaskInfoEntity> taskInfoEntityList = taskRepository.findByTaskIdIn(taskIds);
        if (CollectionUtils.isNotEmpty(taskInfoEntityList))
        {
            return taskInfoEntityList.stream().map(taskInfoEntity ->
            {
                TaskBaseVO taskBaseVO = new TaskBaseVO();
                BeanUtils.copyProperties(taskInfoEntity, taskBaseVO,
                        "taskOwner", "executeDate", "enableToolList", "disableToolList");
                return taskBaseVO;
            }).collect(Collectors.toList());
        }
        else
        {
            return new ArrayList<>();
        }
    }

    /**
     * 设置强制全量扫描标志
     *
     * @param taskEntity
     */
    @Override
    public void setForceFullScan(TaskInfoEntity taskEntity)
    {
        if (CollectionUtils.isNotEmpty(taskEntity.getToolConfigInfoList()))
        {
            List<String> setForceFullScanToolNames = Lists.newArrayList();
            for (ToolConfigInfoEntity toolConfigInfoEntity : taskEntity.getToolConfigInfoList())
            {
                setForceFullScanToolNames.add(toolConfigInfoEntity.getToolName());
            }
            log.info("set force full scan, taskId:{}, toolNames:{}", taskEntity.getTaskId(), setForceFullScanToolNames);
            CodeCCResult<Boolean> toolBuildInfoVOCodeCCResult = client.get(ServiceToolBuildInfoResource.class).setForceFullScan(taskEntity.getTaskId(),
                    setForceFullScanToolNames);
            if (toolBuildInfoVOCodeCCResult == null || toolBuildInfoVOCodeCCResult.isNotOk())
            {
                log.error("set force full san failed! taskId={}, toolNames={}", taskEntity.getScanType(), setForceFullScanToolNames);
                throw new CodeCCException(CommonMessageCode.INTERNAL_SYSTEM_FAIL, new String[]{"set force full san failed!"}, null);
            }
        }
    }

    /**
     * 修改任务扫描触发配置
     *
     * @param taskId
     * @param user
     * @param scanConfigurationVO
     * @return
     */
    @Override
    @OperationHistory(funcId = FUNC_SCAN_SCHEDULE, operType = MODIFY_INFO)
    public Boolean updateScanConfiguration(Long taskId, String user, ScanConfigurationVO scanConfigurationVO)
    {
        // 更新定时分析配置
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        if (scanConfigurationVO.getTimeAnalysisConfig() != null && BsTaskCreateFrom.BS_CODECC.value().equals(taskInfoEntity.getCreateFrom()))
        {
            TimeAnalysisConfigVO timeAnalysisConfigVO = scanConfigurationVO.getTimeAnalysisConfig();
            if (timeAnalysisConfigVO != null)
            {
                // 调用Kotlin方法时需要去掉null
                if (timeAnalysisConfigVO.getExecuteDate() == null)
                {
                    timeAnalysisConfigVO.setExecuteDate(Lists.newArrayList());
                }
                if (timeAnalysisConfigVO.getExecuteTime() == null)
                {
                    timeAnalysisConfigVO.setExecuteTime("");
                }
                //保存任务清单
                pipelineService.modifyCodeCCTiming(taskInfoEntity, timeAnalysisConfigVO.getExecuteDate(), timeAnalysisConfigVO.getExecuteTime(), user);
            }
        }

        // 更新扫描方式
        if (scanConfigurationVO.getScanType() != null)
        {
            taskInfoEntity.setScanType(scanConfigurationVO.getScanType());
        }

        // 更新新告警判定配置
        NewDefectJudgeVO defectJudge = scanConfigurationVO.getNewDefectJudge();
        if (defectJudge != null)
        {
            NewDefectJudgeEntity newDefectJudgeEntity = new NewDefectJudgeEntity();
            BeanUtils.copyProperties(defectJudge, newDefectJudgeEntity);
            if (StringUtils.isNotEmpty(defectJudge.getFromDate()))
            {
                newDefectJudgeEntity.setFromDateTime(DateTimeUtils.convertStringDateToLongTime(defectJudge.getFromDate(), DateTimeUtils.yyyyMMddFormat));
            }
            taskInfoEntity.setNewDefectJudge(newDefectJudgeEntity);
        }

        // 更新告警作者转换配置
        authorTransfer(taskId, scanConfigurationVO, taskInfoEntity);

        taskRepository.save(taskInfoEntity);
        return true;
    }

    @Override
    public Boolean authorTransferForApi(Long taskId, List<ScanConfigurationVO.TransferAuthorPair> transferAuthorPairs, String userId)
    {
        log.info("api author transfer function, user id: {}, task id: {}", userId, taskId);
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        ScanConfigurationVO scanConfigurationVO = new ScanConfigurationVO();
        scanConfigurationVO.setTransferAuthorList(transferAuthorPairs);
        authorTransfer(taskId, scanConfigurationVO, taskInfoEntity);
        return true;
    }

    private void authorTransfer(Long taskId, ScanConfigurationVO scanConfigurationVO, TaskInfoEntity taskInfoEntity)
    {
        AuthorTransferVO authorTransferVO = new AuthorTransferVO();
        authorTransferVO.setTaskId(taskId);
        List<String> tools = toolService.getEffectiveToolList(taskInfoEntity);
        authorTransferVO.setEffectiveTools(tools);
        List<ScanConfigurationVO.TransferAuthorPair> transferAuthorList = scanConfigurationVO.getTransferAuthorList();
        if (CollectionUtils.isNotEmpty(transferAuthorList))
        {
            List<AuthorTransferVO.TransferAuthorPair> newTransferAuthorList = transferAuthorList.stream()
                    .map(authorPair ->
                    {
                        AuthorTransferVO.TransferAuthorPair transferAuthorPair = new AuthorTransferVO.TransferAuthorPair();
                        transferAuthorPair.setSourceAuthor(authorPair.getSourceAuthor());
                        transferAuthorPair.setTargetAuthor(authorPair.getTargetAuthor());
                        return transferAuthorPair;
                    })
                    .collect(Collectors.toList());
            authorTransferVO.setTransferAuthorList(newTransferAuthorList);
        }
        authorTransferBizService.authorTransfer(authorTransferVO);
    }

    /**
     * 更新工具配置信息
     *
     * @param taskDetailVO
     * @param taskEntity
     */
    private void updateToolConfigInfoEntity(TaskDetailVO taskDetailVO, TaskInfoEntity taskEntity, String userName)
    {
        // 获取当前任务的工具的配置信息
        List<ToolConfigInfoEntity> toolConfigList = taskEntity.getToolConfigInfoList();
        // 提交更新任务工具的配置信息
        List<ToolConfigParamJsonVO> updateToolConfigList = taskDetailVO.getDevopsToolParams();

        //根据原有的和提交的，更新工具参数

        if (CollectionUtils.isNotEmpty(toolConfigList) && CollectionUtils.isNotEmpty(updateToolConfigList))
        {
            //提交参数map
            Map<String, String> paramMap = updateToolConfigList.stream().collect(Collectors.toMap(
                    ToolConfigParamJsonVO::getVarName, ToolConfigParamJsonVO::getChooseValue
            ));
            toolConfigList.forEach(toolConfigInfoEntity ->
            {
                ToolMetaBaseVO toolMetaBaseVO = toolMetaCache.getToolBaseMetaCache(toolConfigInfoEntity.getToolName());
                String toolParamJson = toolMetaBaseVO.getParams();
                if (StringUtils.isEmpty(toolParamJson))
                {
                    return;
                }
                //原有参数
                String previousParamJson = toolConfigInfoEntity.getParamJson();
                JSONObject previousParamObj = StringUtils.isNotBlank(previousParamJson) ? JSONObject.fromObject(previousParamJson) : new JSONObject();
                JSONArray toolParamsArray = new JSONArray(toolParamJson);
                for (int i = 0; i < toolParamsArray.length(); i++)
                {
                    org.json.JSONObject paramJsonObj = toolParamsArray.getJSONObject(i);
                    String varName = paramJsonObj.getString("varName");
                    String varValue = paramMap.get(varName);
                    if (StringUtils.isNotEmpty(varValue))
                    {
                        previousParamObj.put(varName, varValue);
                    }
                }
                toolConfigInfoEntity.setParamJson(previousParamObj.toString());
                toolConfigInfoEntity.setUpdatedBy(userName);
                toolConfigInfoEntity.setUpdatedDate(System.currentTimeMillis());
                toolRepository.save(toolConfigInfoEntity);
            });
        }
    }


    /**
     * 获取工具配置Map
     *
     * @param taskEntity
     * @return
     */
    @NotNull
    private Map<String, JSONObject> getToolConfigInfoMap(TaskInfoEntity taskEntity)
    {
        // 获取工具配置的值
        List<ToolConfigInfoEntity> toolConfigInfoList = taskEntity.getToolConfigInfoList();
        Map<String, JSONObject> chooseJsonMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(toolConfigInfoList))
        {
            // 排除下架的工具
            toolConfigInfoList.stream()
                    .filter(config -> config.getFollowStatus() != FOLLOW_STATUS.WITHDRAW.value())
                    .forEach(config ->
                    {
                        String paramJson = config.getParamJson();
                        JSONObject params = new JSONObject();
                        if (StringUtils.isNotBlank(paramJson) && !ComConstants.STRING_NULL_ARRAY.equals(paramJson))
                        {
                            params = JSONObject.fromObject(paramJson);
                        }
                        chooseJsonMap.put(config.getToolName(), params);
                    });
        }
        return chooseJsonMap;
    }


    /**
     * 判断提交的参数是否为空
     *
     * @param taskUpdateVO
     * @return
     */
    private Boolean checkParam(TaskUpdateVO taskUpdateVO)
    {
        if (StringUtils.isBlank(taskUpdateVO.getNameCn()))
        {
            return false;
        }
        return taskUpdateVO.getCodeLang() > 0;
    }


    /**
     * 给工具分类及排序
     *
     * @param taskBaseVO
     * @param toolEntityList
     */
    private void sortedToolList(TaskBaseVO taskBaseVO, List<ToolConfigInfoEntity> toolEntityList)
    {
        // 如果工具不为空，对工具排序并且赋值工具展示名
        if (CollectionUtils.isNotEmpty(toolEntityList))
        {
            List<ToolConfigBaseVO> enableToolList = new ArrayList<>();
            List<ToolConfigBaseVO> disableToolList = new ArrayList<>();

            List<String> toolIDArr = getToolOrders();
            for (String toolName : toolIDArr)
            {
                for (ToolConfigInfoEntity toolEntity : toolEntityList)
                {
                    if (toolName.equals(toolEntity.getToolName()))
                    {
                        ToolConfigBaseVO toolConfigBaseVO = new ToolConfigBaseVO();
                        BeanUtils.copyProperties(toolEntity, toolConfigBaseVO);
                        ToolMetaBaseVO toolMetaBaseVO = toolMetaCache.getToolBaseMetaCache(toolName);
                        toolConfigBaseVO.setToolDisplayName(toolMetaBaseVO.getDisplayName());
                        toolConfigBaseVO.setToolPattern(toolMetaBaseVO.getPattern());
                        toolConfigBaseVO.setToolType(toolMetaBaseVO.getType());

                        // 加入规则集
                        if (toolEntity.getCheckerSet() != null)
                        {
                            CheckerSetVO checkerSetVO = new CheckerSetVO();
                            BeanUtils.copyProperties(toolEntity.getCheckerSet(), checkerSetVO);
                            toolConfigBaseVO.setCheckerSet(checkerSetVO);
                        }

                        if (TaskConstants.FOLLOW_STATUS.WITHDRAW.value() == toolConfigBaseVO.getFollowStatus())
                        {
                            disableToolList.add(toolConfigBaseVO);
                        }
                        else
                        {
                            enableToolList.add(toolConfigBaseVO);
                        }
                    }
                }
            }

            taskBaseVO.setEnableToolList(enableToolList);
            taskBaseVO.setDisableToolList(disableToolList);
        }
    }


    /**
     * 重置工具的顺序，数据库中工具是按接入的先后顺序排序的，前端展示要按照工具类型排序
     *
     * @param toolNames
     * @param toolIdsOrder
     * @return
     */
    private String resetToolOrderByType(String toolNames, final String toolIdsOrder, List<ToolConfigInfoVO> unsortedToolList,
                                        List<ToolConfigInfoVO> sortedToolList)
    {
        if (StringUtils.isEmpty(toolNames))
        {
            return null;
        }

        String[] toolNamesArr = toolNames.split(",");
        List<String> originToolList = Arrays.asList(toolNamesArr);
        String[] toolIDArr = toolIdsOrder.split(",");
        List<String> orderToolList = Arrays.asList(toolIDArr);
        Iterator<String> it = orderToolList.iterator();
        StringBuffer sb = new StringBuffer();
        while (it.hasNext())
        {
            String toolId = it.next();
            if (originToolList.contains(toolId))
            {
                sb.append(toolId).append(",");
                List<ToolConfigInfoVO> filteredList = unsortedToolList.stream().
                        filter(toolConfigInfoVO ->
                                toolId.equalsIgnoreCase(toolConfigInfoVO.getToolName())
                        ).
                        collect(Collectors.toList());

                sortedToolList.addAll(CollectionUtils.isNotEmpty(filteredList) ? filteredList : Collections.EMPTY_LIST);
            }
        }
        if (sb.length() > 0)
        {
            toolNames = sb.substring(0, sb.length() - 1);
        }

        return toolNames;
    }


    private TaskListVO sortByDate(List<TaskDetailVO> taskDetailVOS, TaskSortType taskSortType)
    {
        TaskListVO taskList = new TaskListVO(Collections.emptyList(), Collections.emptyList());
        List<TaskDetailVO> enableProjs = new ArrayList<>();
        List<TaskDetailVO> disableProjs = new ArrayList<>();
        for (TaskDetailVO taskDetailVO : taskDetailVOS)
        {
            if (!TaskConstants.TaskStatus.DISABLE.value().equals(taskDetailVO.getStatus()))
            {
                enableProjs.add(taskDetailVO);
            }
            else
            {
                disableProjs.add(taskDetailVO);
            }
        }
        if (CollectionUtils.isNotEmpty(taskDetailVOS))
        {
            //分离已启用项目和停用项目

            //启用的项目按创建时间倒排,如果有置顶就放在最前面
            switch (taskSortType)
            {
                case CREATE_DATE:
                    enableProjs.sort((o1, o2) ->
                            o2.getTopFlag() - o1.getTopFlag() == 0 ?
                                    o2.getCreatedDate().compareTo(o1.getCreatedDate()) : o2.getTopFlag() - o1.getTopFlag()
                    );
                    break;
                case LAST_EXECUTE_DATE:
                    enableProjs.sort((o1, o2) ->
                            o2.getTopFlag() - o1.getTopFlag() == 0 ?
                                    o2.getMinStartTime().compareTo(o1.getMinStartTime()) : o2.getTopFlag() - o1.getTopFlag()
                    );
                    break;
                case SIMPLIFIED_PINYIN:
                    enableProjs.sort((o1, o2) ->
                            o2.getTopFlag() - o1.getTopFlag() == 0 ?
                                    Collator.getInstance(Locale.TRADITIONAL_CHINESE).compare(StringUtils.isNotBlank(o1.getNameCn()) ? o1.getNameCn() : o1.getNameEn(),
                                            StringUtils.isNotBlank(o2.getNameCn()) ? o2.getNameCn() : o2.getNameEn()) :
                                    o2.getTopFlag() - o1.getTopFlag()
                    );
                    break;
                default:
                    enableProjs.sort((o1, o2) ->
                            o2.getTopFlag() - o1.getTopFlag() == 0 ?
                                    o2.getCreatedDate().compareTo(o1.getCreatedDate()) : o2.getTopFlag() - o1.getTopFlag()
                    );
                    break;
            }


            //重建projectList
            taskList.setEnableTasks(enableProjs);
            taskList.setDisableTasks(disableProjs);
        }
        return taskList;
    }

    /**
     * 获取工具排序
     *
     * @return
     */
    private List<String> getToolOrders()
    {
        String toolIdsOrder = commonDao.getToolOrder();
        return List2StrUtil.fromString(toolIdsOrder, ComConstants.STRING_SPLIT);
    }

    /**
     * 获取工具特殊参数列表
     *
     * @param paramJsonStr
     * @return
     */
    private List<PipelineToolParamVO> getParams(String paramJsonStr)
    {
        List<PipelineToolParamVO> params = Lists.newArrayList();
        if (StringUtils.isNotEmpty(paramJsonStr))
        {
            JSONObject paramJson = JSONObject.fromObject(paramJsonStr);
            if (paramJson != null && !paramJson.isNullObject())
            {
                for (Object paramKeyObj : paramJson.keySet())
                {
                    String paramKey = (String) paramKeyObj;
                    PipelineToolParamVO pipelineToolParamVO = new PipelineToolParamVO(paramKey, paramJson.getString(paramKey));
                    params.add(pipelineToolParamVO);
                }
            }
        }
        return params;
    }


    /**
     * 根据条件获取任务基本信息清单
     *
     * @param taskListReqVO 请求体对象
     * @return list
     */
    @Override
    public TaskListVO getTaskDetailList(QueryTaskListReqVO taskListReqVO)
    {
        Integer taskStatus = taskListReqVO.getStatus();
        String toolName = taskListReqVO.getToolName();
        Integer bgId = taskListReqVO.getBgId();
        Integer deptId = taskListReqVO.getDeptId();
        List<String> createFrom = taskListReqVO.getCreateFrom();
        Boolean isExcludeTaskIds = Boolean.valueOf(taskListReqVO.getIsExcludeTaskIds());
        List<Long> taskIdsReq = Lists.newArrayList(taskListReqVO.getTaskIds());

        List<Integer> deptIds = null;
        if (deptId != null && deptId != 0)
        {
            deptIds = Lists.newArrayList(deptId);
        }

        TaskListVO taskList = new TaskListVO(Collections.emptyList(), Collections.emptyList());
        List<TaskDetailVO> tasks = new ArrayList<>();

        // 根据isExcludeTaskIds来判断参数taskIdsReq 的处理方式，来获取任务ID列表
        List<Long> queryTaskIds = getTaskIdListByFlag(toolName, isExcludeTaskIds, taskIdsReq);

        // 根据任务状态获取注册过该工具的任务列表
        List<TaskInfoEntity> taskInfoEntityList =
                taskDao.queryTaskInfoEntityList(taskStatus, bgId, deptIds, queryTaskIds, createFrom);
        if (CollectionUtils.isNotEmpty(taskInfoEntityList))
        {
            taskInfoEntityList.forEach(entity ->
            {
                TaskDetailVO taskDetailVO = new TaskDetailVO();
                BeanUtils.copyProperties(entity, taskDetailVO);
                tasks.add(taskDetailVO);
            });
        }

        if (Status.ENABLE.value() == taskStatus)
        {
            taskList.setEnableTasks(tasks);
        }
        else
        {
            taskList.setDisableTasks(tasks);
        }
        return taskList;
    }

    /**
     * 根据isExcludeTaskIds来判断参数taskIdsReq 的处理方式，来获取任务ID列表
     *
     * @param toolName         工具名称
     * @param isExcludeTaskIds true: 排除taskIdsReq false: 从taskIdsReq排除
     * @param taskIdsReq       参数(任务ID列表)
     * @return task id list
     */
    @NotNull
    private List<Long> getTaskIdListByFlag(String toolName, Boolean isExcludeTaskIds, List<Long> taskIdsReq)
    {
        List<Long> queryTaskIds;
        if (BooleanUtils.isTrue(isExcludeTaskIds))
        {
            List<Long> notWithdrawTasks = Lists.newArrayList();
            List<ToolConfigInfoEntity> toolConfigInfos =
                    toolRepository.findByToolNameAndFollowStatusNot(toolName, FOLLOW_STATUS.WITHDRAW.value());
            if (CollectionUtils.isNotEmpty(toolConfigInfos))
            {
                toolConfigInfos.forEach(entity -> notWithdrawTasks.add(entity.getTaskId()));
            }
            // 剔除参数taskListReqVO的任务
            notWithdrawTasks.removeAll(taskIdsReq);
            queryTaskIds = notWithdrawTasks;
        }
        else
        {
            List<Long> withdrawTasks = Lists.newArrayList();
            List<ToolConfigInfoEntity> toolConfigInfos = toolRepository.findByToolNameAndFollowStatusIs(toolName,
                    FOLLOW_STATUS.WITHDRAW.value());
            if (CollectionUtils.isNotEmpty(toolConfigInfos))
            {
                toolConfigInfos.forEach(entity -> withdrawTasks.add(entity.getTaskId()));
            }
            // 剔除已下架该工具的任务
            taskIdsReq.removeAll(withdrawTasks);
            queryTaskIds = taskIdsReq;
        }
        return queryTaskIds;
    }


    @Override
    public Page<TaskInfoVO> getTasksByAuthor(QueryMyTasksReqVO reqVO)
    {
        checkParam(reqVO);
        String repoUrl = reqVO.getRepoUrl();
        String branch = reqVO.getBranch();

        List<TaskInfoVO> tasks = Lists.newArrayList();

        List<TaskInfoEntity> allUserTasks =
                taskRepository.findTaskList(reqVO.getAuthor(), TaskConstants.TaskStatus.ENABLE.value());

        if (CollectionUtils.isNotEmpty(allUserTasks))
        {
            Set<String> taskProjectIdList = Sets.newHashSet();
            allUserTasks.forEach(task ->
            {
                String bkProjectId = task.getProjectId();
                if (StringUtils.isNotEmpty(bkProjectId))
                {
                    taskProjectIdList.add(bkProjectId);
                }
            });

            Map<String, RepoInfoVO> repoInfoVoMap = pipelineService.getRepoUrlByBkProjects(taskProjectIdList);
            String repoHashId = "";
            RepoInfoVO repoInfoVO = repoInfoVoMap.get(repoUrl);
            if (repoInfoVO != null)
            {
                repoHashId = repoInfoVO.getRepoHashId();
            }

            for (TaskInfoEntity task : allUserTasks)
            {
                // 过滤任务
                if (taskFilterIsTrue(branch, repoHashId, task))
                {
                    continue;
                }

                TaskInfoVO taskInfoVO = new TaskInfoVO();
                taskInfoVO.setTaskId(task.getTaskId());
                taskInfoVO.setNameCn(task.getNameCn());
                taskInfoVO.setProjectId(task.getProjectId());

                List<String> tools = Lists.newArrayList();
                task.getToolConfigInfoList().forEach(toolInfo ->
                {
                    // 过滤掉已停用
                    if (toolInfo != null && toolInfo.getFollowStatus() != FOLLOW_STATUS.WITHDRAW.value())
                    {
                        tools.add(toolInfo.getToolName());
                    }
                });

                taskInfoVO.setToolNames(tools);
                tasks.add(taskInfoVO);
            }
        }

        return sortAndPage(reqVO.getPageNum(), reqVO.getPageSize(), reqVO.getSortType(), reqVO.getSortField(), tasks);
    }

    @Override
    public void updateReportInfo(Long taskId, NotifyCustomVO notifyCustomVO)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        NotifyCustomEntity previousNofityEntity = taskInfoEntity.getNotifyCustomInfo();
        OperationType operationType;
        if (null != previousNofityEntity &&
                CollectionUtils.isNotEmpty(previousNofityEntity.getReportDate()) &&
                null != previousNofityEntity.getReportTime() &&
                CollectionUtils.isNotEmpty(previousNofityEntity.getReportTools()))
        {
            operationType = OperationType.RESCHEDULE;
        }
        else
        {
            operationType = OperationType.ADD;
        }
        NotifyCustomEntity notifyCustomEntity = new NotifyCustomEntity();
        BeanUtils.copyProperties(notifyCustomVO, notifyCustomEntity);
        //如果定时任务信息不为空，则与定时调度平台通信
        if (CollectionUtils.isNotEmpty(notifyCustomVO.getReportDate()) &&
                null != notifyCustomVO.getReportTime() &&
                CollectionUtils.isNotEmpty(notifyCustomVO.getReportTools()))
        {
            String jobName = emailNotifyService.addEmailScheduleTask(taskId, notifyCustomVO.getReportDate(),
                    notifyCustomVO.getReportTime(), operationType, null == previousNofityEntity ? null : previousNofityEntity.getReportJobName());
            notifyCustomEntity.setReportJobName(jobName);
        }

        taskInfoEntity.setNotifyCustomInfo(notifyCustomEntity);
        taskRepository.save(taskInfoEntity);
    }

    @Override
    public Boolean updateTopUserInfo(Long taskId, String user, Boolean topFlag)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        if (null == taskInfoEntity)
        {
            return false;
        }
        Set<String> topUser = taskInfoEntity.getTopUser();
        //如果是置顶操作
        if (topFlag)
        {
            if (CollectionUtils.isEmpty(topUser))
            {
                taskInfoEntity.setTopUser(new HashSet<String>()
                {{
                    add(user);
                }});
            }
            else
            {
                topUser.add(user);
            }
        }
        //如果是取消置顶操作
        else
        {
            if (CollectionUtils.isEmpty(topUser))
            {
                log.error("top user list is empty! task id: {}", taskId);
                return false;
            }
            else
            {
                topUser.remove(user);
            }
        }

        taskRepository.save(taskInfoEntity);
        return true;
    }

    @Override
    public TaskInfoEntity getTaskByGongfengId(Integer gongfengProjectId)
    {
        return taskRepository.findByGongfengProjectId(gongfengProjectId);
    }

    @Override
    public List<TaskDetailVO> getTaskInfoList(QueryTaskListReqVO taskListReqVO)
    {
        List<TaskInfoEntity> taskInfoEntityList =
                taskDao.queryTaskInfoEntityList(taskListReqVO.getStatus(), taskListReqVO.getBgId(),
                        taskListReqVO.getDeptIds(), taskListReqVO.getTaskIds(), taskListReqVO.getCreateFrom());

        return entities2TaskDetailVoList(taskInfoEntityList);
    }

    @Override
    public Page<TaskDetailVO> getTaskDetailPage(@NotNull QueryTaskListReqVO reqVO)
    {
        Sort.Direction direction = Sort.Direction.valueOf(reqVO.getSortType());
        Pageable pageable = PageableUtils
                .getPageable(reqVO.getPageNum(), reqVO.getPageSize(), reqVO.getSortField(), direction, "task_id");

        org.springframework.data.domain.Page<TaskInfoEntity> entityPage = taskRepository
                .findByStatusAndBgIdAndDeptIdInAndCreateFromIn(reqVO.getStatus(), reqVO.getBgId(), reqVO.getDeptIds(),
                        reqVO.getCreateFrom(), pageable);
        List<TaskInfoEntity> taskInfoEntityList = entityPage.getContent();

        List<TaskDetailVO> taskInfoList = entities2TaskDetailVoList(taskInfoEntityList);

        // 页码+1展示
        return new Page<>(entityPage.getTotalElements(), entityPage.getNumber() + 1, entityPage.getSize(),
                entityPage.getTotalPages(), taskInfoList);
    }

    @NotNull
    private List<TaskDetailVO> entities2TaskDetailVoList(List<TaskInfoEntity> taskInfoEntityList)
    {
        List<TaskDetailVO> taskInfoList = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(taskInfoEntityList))
        {
            taskInfoList = taskInfoEntityList.stream().map(taskInfoEntity ->
            {
                TaskDetailVO taskDetailVO = new TaskDetailVO();
                BeanUtils.copyProperties(taskInfoEntity, taskDetailVO);
                return taskDetailVO;
            }).collect(Collectors.toList());
        }
        return taskInfoList;
    }


    private boolean taskFilterIsTrue(String branch, String repoHashId, TaskInfoEntity task)
    {
        // 如果不是工蜂代码扫描创建的任务
        String createFrom = task.getCreateFrom();
        if (!BsTaskCreateFrom.GONGFENG_SCAN.value().equals(createFrom))
        {
            // 过滤代码库不匹配的
            String taskRepoHashId = task.getRepoHashId();
            if (StringUtils.isBlank(taskRepoHashId) || !taskRepoHashId.equals(repoHashId))
            {
                return true;
            }
            // 过滤分支不符合的，参数branch为null则不检查
            if (branch != null && !branch.equals(task.getBranch()))
            {
                return true;
            }
        }
        // 过滤未添加工具的
        return task.getToolConfigInfoList() == null;
    }


    /**
     * 检查参数并赋默认值
     *
     * @param reqVO req
     */
    private void checkParam(QueryMyTasksReqVO reqVO)
    {
        if (reqVO.getPageNum() == null)
        {
            reqVO.setPageNum(1);
        }

        if (reqVO.getPageSize() == null)
        {
            reqVO.setPageSize(10);
        }

        if (reqVO.getSortField() == null)
        {
            reqVO.setSortField("taskId");
        }
    }


    @NotNull
    private Page<TaskInfoVO> sortAndPage(int pageNum, int pageSize, String sortType, String sortField,
                                         List<TaskInfoVO> tasks)
    {
        if (!Sort.Direction.ASC.name().equalsIgnoreCase(sortType))
        {
            sortType = Sort.Direction.DESC.name();
        }
        ListSortUtil.sort(tasks, sortField, sortType);

        int totalPageNum = 0;
        int total = tasks.size();
        pageNum = pageNum - 1 < 0 ? 0 : pageNum - 1;
        pageSize = pageSize <= 0 ? 10 : pageSize;
        if (total > 0)
        {
            totalPageNum = (total + pageSize - 1) / pageSize;
        }

        int subListBeginIdx = pageNum * pageSize;
        int subListEndIdx = subListBeginIdx + pageSize;
        if (subListBeginIdx > total)
        {
            subListBeginIdx = 0;
        }
        List<TaskInfoVO> taskInfoVoList = tasks.subList(subListBeginIdx, subListEndIdx > total ? total : subListEndIdx);

        return new Page<>(total, pageNum == 0 ? 1 : pageNum, pageSize, totalPageNum, taskInfoVoList);
    }


    @Override
    public Set<Integer> queryDeptIdByBgId(Integer bgId)
    {
        // 指定工蜂扫描的部门ID
        List<TaskInfoEntity> deptIdList = taskDao.queryDeptId(bgId, BsTaskCreateFrom.GONGFENG_SCAN.value());

        return deptIdList.stream().filter(elem -> elem.getDeptId() > 0).map(TaskInfoEntity::getDeptId)
                .collect(Collectors.toSet());
    }

    @Override
    public Boolean refreshTaskOrgInfo(Long taskId)
    {
        boolean result = false;
        if (taskId != null && taskId != 0)
        {
            TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
            if (taskInfoEntity == null)
            {
                log.error("refreshTaskOrgInfo infoEntity is not found: {}", taskId);
                throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS);
            }

            DevopsProjectOrgVO devopsProjectOrg = userManageService.getDevopsProjectOrg(taskInfoEntity.getProjectId());
            Integer bgId = devopsProjectOrg.getBgId();
            if (bgId == null || bgId <= 0)
            {
                TofStaffInfo staffInfo =
                        tofClientApi.getStaffInfoByUserName(taskInfoEntity.getTaskOwner().get(0)).getData();
                if (staffInfo == null)
                {
                    log.error("getStaffInfoByUserName is null: {}", taskId);
                    throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS);
                }
                TofOrganizationInfo orgInfo = tofClientApi.getOrganizationInfoByGroupId(staffInfo.getGroupId());
                if (orgInfo == null)
                {
                    log.error("getOrganizationInfoByGroupId is null: {}", taskId);
                    throw new CodeCCException(CommonMessageCode.RECORD_NOT_EXITS);
                }
                devopsProjectOrg.setBgId(orgInfo.getBgId());
                devopsProjectOrg.setDeptId(orgInfo.getDeptId());
                devopsProjectOrg.setCenterId(orgInfo.getCenterId());
            }

            taskInfoEntity.setBgId(devopsProjectOrg.getBgId());
            taskInfoEntity.setDeptId(devopsProjectOrg.getDeptId());
            taskInfoEntity.setCenterId(devopsProjectOrg.getCenterId());

            result = taskDao.updateOrgInfo(taskInfoEntity);
        }
        return result;
    }

    @Override
    public void updateTaskOwnerAndMember(TaskOwnerAndMemberVO taskOwnerAndMemberVO, Long taskId)
    {
        TaskInfoEntity taskInfoEntity = taskRepository.findByTaskId(taskId);
        if(null == taskInfoEntity)
        {
            return;
        }
        taskInfoEntity.setTaskMember(taskOwnerAndMemberVO.getTaskMember());
        taskInfoEntity.setTaskOwner(taskOwnerAndMemberVO.getTaskOwner());
        taskRepository.save(taskInfoEntity);
    }
}
