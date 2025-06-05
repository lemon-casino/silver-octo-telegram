package cn.iocoder.yudao.module.bpm.service.task.cmd;

/**
 * @Package: cn.iocoder.yudao.module.bpm.service.task.cmd
 * @Description: < >
 * @Author: 柠檬果肉
 * @Date: 2025/6/4 21:37
 * @Version V1.0
 */

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableJumpUtils;
import com.google.common.collect.Sets;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.engine.runtime.ActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 重新触发任务执行命令
 */
public class ReloadTaskCmd implements Command<String>, Serializable {

    private static final long serialVersionUID = 1L;

    protected RuntimeService runtimeService;
    protected String businessKey;
    protected String targetActivityId;

    public ReloadTaskCmd(RuntimeService runtimeService, String businessKey, String targetActivityId) {
        this.runtimeService = runtimeService;
        this.businessKey = businessKey;
        this.targetActivityId = targetActivityId;
    }

    @Override
    public String execute(CommandContext commandContext) {
        if (StrUtil.isBlank(targetActivityId)) {
            throw new FlowableException("TargetActivityId cannot be empty");
        }
        if (StrUtil.isBlank(businessKey)) {
            throw new FlowableException("BusinessKey cannot be empty");
        }
        String sourceActivityId = null;
        String processInstanceId = null;
        String processDefinitionId = null;
        String executionId = null;
        ActivityInstance activityInstance = null;
        List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).list();
        for (ProcessInstance processInstance : processInstanceList) {
            List<ActivityInstance> activityInstances = runtimeService.createActivityInstanceQuery()
                    .processInstanceId(processInstance.getProcessInstanceId())
                    .activityId(targetActivityId)
                    .orderByActivityInstanceStartTime().desc().list();
            if (CollUtil.isNotEmpty(activityInstances)) {
                activityInstance = activityInstances.get(0);
                sourceActivityId = activityInstance.getActivityId();
                processInstanceId = activityInstance.getProcessInstanceId();
                processDefinitionId = activityInstance.getProcessDefinitionId();
                executionId = activityInstance.getExecutionId();
                break;
            }
        }
        if (activityInstance == null) {
            for (ProcessInstance processInstance : processInstanceList) {
                List<ExecutionEntity> executionEntitys = CommandContextUtil.getExecutionEntityManager()
                        .findExecutionsByParentExecutionAndActivityIds(processInstance.getProcessInstanceId(), Collections.singleton(targetActivityId));
                if (CollUtil.isNotEmpty(executionEntitys)) {
                    ExecutionEntity executionEntity = executionEntitys.stream().max(Comparator.comparing(ExecutionEntity::getStartTime)).orElse(null);
                    sourceActivityId = executionEntity.getActivityId();
                    processInstanceId = executionEntity.getProcessInstanceId();
                    processDefinitionId = executionEntity.getProcessDefinitionId();
                    executionId = executionEntity.getId();
                    break;
                }
            }
        }
        if (sourceActivityId == null) {
            throw new FlowableObjectNotFoundException("targetActivity: " + targetActivityId + " does not exist");
        }

        Process process = ProcessDefinitionUtil.getProcess(processDefinitionId);
        FlowNode sourceFlowElement = (FlowNode) process.getFlowElement(sourceActivityId, true);
        FlowNode targetFlowElement = (FlowNode) process.getFlowElement(targetActivityId, true);
        if (!FlowableJumpUtils.isReachable(process, targetFlowElement, sourceFlowElement)) {
            throw new FlowableException("Cannot back to [" + targetActivityId + "]");
        }
        String[] sourceAndTargetRealActivityId = FlowableJumpUtils.getSourceAndTargetRealActivityId(sourceFlowElement,
                targetFlowElement);
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
        } else if (targetInSpecialGatewayList.isEmpty() && !sourceInSpecialGatewayList.isEmpty()) {
            sourceRealAcitivtyIds = specialGatewayNodes.get(sourceInSpecialGatewayList.get(0));
        } else if (!targetInSpecialGatewayList.isEmpty() && sourceInSpecialGatewayList.isEmpty()) {
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
                                                           List<ExecutionEntity> excutionEntitys, Process process,
                                                           List<String> targetInSpecialGatewayList,
                                                           String targetRealSpecialGateway) {
        String parentExecutionId = excutionEntitys.iterator().next().getParentId();
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
        String realParentExecutionId = FlowableJumpUtils.getParentExecutionIdFromParentIds(taskExecution, parentExecutionIds);
        List<ExecutionEntity>  executionEntity= executionEntityManager.findExecutionsByParentExecutionAndActivityIds(realParentExecutionId, activityIds);
        if (executionEntity.isEmpty()) {
            // 当未查询到符合条件的执行时，默认使用当前任务对应的执行，避免未能产生目标节点
            executionEntity = Collections.singletonList(taskExecution);
        }
        return executionEntity;
    }
}