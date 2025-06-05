package cn.iocoder.yudao.module.bpm.service.task.cmd;

/**
 * @Package: cn.iocoder.yudao.module.bpm.service.task.cmd
 * @Description: < >
 * @Author: 柠檬果肉
 * @Date: 2025/6/4 21:36
 * @Version V1.0
 */

import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableJumpUtils;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ActivityInstanceEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务自由跳转命令
 */
@Slf4j
public class BackTaskCmd implements Command<String>, Serializable {

    private static final long serialVersionUID = 1L;

    private final transient RuntimeService runtimeService;

    /** 当前运行的任务、流程节点或执行 ID */
    protected String taskIdOrActivityIdOrExecutionId;

    /** 需要跳转的目标节点 ID */
    protected String targetActivityId;

    public BackTaskCmd(RuntimeService runtimeService, String taskId, String targetActivityId) {
        this.runtimeService = runtimeService;
        this.taskIdOrActivityIdOrExecutionId = taskId;
        this.targetActivityId = targetActivityId;
    }

    @Override
    public String execute(CommandContext commandContext) {
        if (targetActivityId == null || targetActivityId.length() == 0) {
            throw new FlowableException("TargetActivityId cannot be empty");
        }
        TaskEntity task = CommandContextUtil.getProcessEngineConfiguration().getTaskServiceConfiguration()
                .getTaskService().getTask(taskIdOrActivityIdOrExecutionId);
        String sourceActivityId = null;
        String processInstanceId = null;
        String processDefinitionId = null;
        String executionId = null;
        if (task != null) {
            sourceActivityId = task.getTaskDefinitionKey();
            processInstanceId = task.getProcessInstanceId();
            processDefinitionId = task.getProcessDefinitionId();
            executionId = task.getExecutionId();
        } else {
            ActivityInstanceEntity instanceEntity = CommandContextUtil.getActivityInstanceEntityManager()
                    .findById(taskIdOrActivityIdOrExecutionId);
            if (instanceEntity != null) {
                sourceActivityId = instanceEntity.getProcessInstanceId();
                processInstanceId = instanceEntity.getActivityId();
                processDefinitionId = instanceEntity.getProcessDefinitionId();
                executionId = instanceEntity.getExecutionId();
            } else {
                ExecutionEntity executionEntity = CommandContextUtil.getExecutionEntityManager()
                        .findById(taskIdOrActivityIdOrExecutionId);
                if (executionEntity != null) {
                    sourceActivityId = executionEntity.getActivityId();
                    processInstanceId = executionEntity.getProcessInstanceId();
                    processDefinitionId = executionEntity.getProcessDefinitionId();
                    executionId = executionEntity.getId();
                }
            }
        }

        log.info("[BackTaskCmd] start: taskOrActivity={}, processInstanceId={}, sourceActivity={}, targetActivity={}",
                taskIdOrActivityIdOrExecutionId, processInstanceId, sourceActivityId, targetActivityId);

        if (sourceActivityId == null) {
            throw new FlowableObjectNotFoundException("task " + taskIdOrActivityIdOrExecutionId + " doesn't exist", Task.class);
        }

        Process process = ProcessDefinitionUtil.getProcess(processDefinitionId);
        FlowNode sourceFlowElement = (FlowNode) process.getFlowElement(sourceActivityId, true);
        if (!(sourceFlowElement instanceof UserTask)) {
            // 默认仅支持用户任务，可根据需要扩展
        }
        FlowNode targetFlowElement = (FlowNode) process.getFlowElement(targetActivityId, true);
        if (!FlowableJumpUtils.isReachable(process, targetFlowElement, sourceFlowElement)) {
            throw new FlowableException("Cannot back to [" + targetActivityId + "]");
        }
        String[] sourceAndTargetRealActivityId = FlowableJumpUtils.getSourceAndTargetRealActivityId(sourceFlowElement, targetFlowElement);
        String sourceRealActivityId = sourceAndTargetRealActivityId[0];
        String targetRealActivityId = sourceAndTargetRealActivityId[1];

        Map<String, Set<String>> specialGatewayNodes = FlowableJumpUtils.getSpecialGatewayElements(process);
        List<String> sourceInSpecialGatewayList = new ArrayList<>();
        List<String> targetInSpecialGatewayList = new ArrayList<>();
        setSpecialGatewayList(sourceRealActivityId, targetRealActivityId, specialGatewayNodes,
                sourceInSpecialGatewayList, targetInSpecialGatewayList);

        Set<String> sourceRealAcitivtyIds = null;
        String targetRealSpecialGateway = null;

        if (targetInSpecialGatewayList.isEmpty() && sourceInSpecialGatewayList.isEmpty()) {
            sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
        } else if (targetInSpecialGatewayList.isEmpty()) {
            sourceRealAcitivtyIds = specialGatewayNodes.get(sourceInSpecialGatewayList.get(0));
        } else if (sourceInSpecialGatewayList.isEmpty()) {
            sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
            targetRealSpecialGateway = targetInSpecialGatewayList.get(0);
        } else {
            int diffSpecialGatewayLevel = FlowableJumpUtils.getDiffLevel(sourceInSpecialGatewayList, targetInSpecialGatewayList);
            if (diffSpecialGatewayLevel == -1) {
                sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
            } else {
                if (sourceInSpecialGatewayList.size() == diffSpecialGatewayLevel) {
                    sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
                } else {
                    sourceRealAcitivtyIds = specialGatewayNodes.get(sourceInSpecialGatewayList.get(diffSpecialGatewayLevel));
                }
                if (targetInSpecialGatewayList.size() != diffSpecialGatewayLevel) {
                    targetRealSpecialGateway = targetInSpecialGatewayList.get(diffSpecialGatewayLevel);
                }
            }
        }

        List<ExecutionEntity> realExecutions = getRealExecutions(commandContext, processInstanceId, executionId,
                sourceRealActivityId, sourceRealAcitivtyIds);
        List<String> realExecutionIds = realExecutions.stream().map(ExecutionEntity::getId).collect(Collectors.toList());
        log.info("[BackTaskCmd] moving executions {} to {}", realExecutionIds, targetRealActivityId);
        runtimeService.createChangeActivityStateBuilder().processInstanceId(processInstanceId)
                .moveExecutionsToSingleActivityId(realExecutionIds, targetRealActivityId).changeState();
        if (targetRealSpecialGateway != null) {
            createTargetInSpecialGatewayEndExecutions(commandContext, realExecutions, process,
                    targetInSpecialGatewayList, targetRealSpecialGateway);
        }
        return targetRealActivityId;
    }

    private void setSpecialGatewayList(String sourceActivityId, String targetActivityId,
                                       Map<String, Set<String>> specialGatewayNodes,
                                       List<String> sourceInSpecialGatewayList,
                                       List<String> targetInSpecialGatewayList) {
        for (Map.Entry<String, Set<String>> entry : specialGatewayNodes.entrySet()) {
            if (entry.getValue().contains(sourceActivityId)) {
                sourceInSpecialGatewayList.add(entry.getKey());
            }
            if (entry.getValue().contains(targetActivityId)) {
                targetInSpecialGatewayList.add(entry.getKey());
            }
        }
    }

    private void createTargetInSpecialGatewayEndExecutions(CommandContext commandContext,
                                                           List<ExecutionEntity> executionEntitys, Process process,
                                                           List<String> targetInSpecialGatewayList,
                                                           String targetRealSpecialGateway) {
        String parentExecutionId = executionEntitys.iterator().next().getParentId();
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity parentExecutionEntity = executionEntityManager.findById(parentExecutionId);

        int index = targetInSpecialGatewayList.indexOf(targetRealSpecialGateway);
        for (; index < targetInSpecialGatewayList.size(); index++) {
            String targetInSpecialGateway = targetInSpecialGatewayList.get(index);
            String targetInSpecialGatewayEndId = targetInSpecialGateway + BpmnModelConstants.SPECIAL_GATEWAY_END_SUFFIX;
            FlowNode targetInSpecialGatewayEnd = (FlowNode) process.getFlowElement(targetInSpecialGatewayEndId, true);
            if (targetInSpecialGatewayEnd == null) {
                targetInSpecialGatewayEndId = targetInSpecialGateway + BpmnModelConstants.SPECIAL_GATEWAY_JOIN_SUFFIX;
                targetInSpecialGatewayEnd = (FlowNode) process.getFlowElement(targetInSpecialGatewayEndId, true);
            }
            int nbrOfExecutionsToJoin = targetInSpecialGatewayEnd.getIncomingFlows().size();
            for (int i = 0; i < nbrOfExecutionsToJoin - 1; i++) {
                ExecutionEntity childExecution = executionEntityManager.createChildExecution(parentExecutionEntity);
                childExecution.setCurrentFlowElement(targetInSpecialGatewayEnd);
                ActivityBehavior activityBehavior = (ActivityBehavior) targetInSpecialGatewayEnd.getBehavior();
                activityBehavior.execute(childExecution);
            }
        }
    }

    private List<ExecutionEntity> getRealExecutions(CommandContext commandContext, String processInstanceId,
                                                    String taskExecutionId, String sourceRealActivityId,
                                                    Set<String> activityIds) {
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity taskExecution = executionEntityManager.findById(taskExecutionId);
        List<ExecutionEntity> executions = executionEntityManager.findChildExecutionsByProcessInstanceId(processInstanceId);
        Set<String> parentExecutionIds = FlowableJumpUtils.getParentExecutionIdsByActivityId(executions, sourceRealActivityId);
        String realParentExecutionId;
        try {
            realParentExecutionId = FlowableJumpUtils.getParentExecutionIdFromParentIds(taskExecution, parentExecutionIds);
        } catch (FlowableException e) {
            log.warn("[BackTaskCmd] parent execution not found for {} using task parent {}", taskExecutionId, taskExecution.getParentId());
            realParentExecutionId = taskExecution.getParentId();
        }
        List<ExecutionEntity> realExecutions = executionEntityManager.findExecutionsByParentExecutionAndActivityIds(realParentExecutionId, activityIds);
        if (realExecutions.isEmpty() && taskExecution != null) {
            log.info("[BackTaskCmd] no executions found, fallback to current execution {}", taskExecutionId);
            realExecutions = Collections.singletonList(taskExecution);
        }
        return realExecutions;
    }
}