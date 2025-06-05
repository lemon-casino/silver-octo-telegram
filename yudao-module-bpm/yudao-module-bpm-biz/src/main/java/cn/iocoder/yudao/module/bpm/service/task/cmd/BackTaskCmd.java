package cn.iocoder.yudao.module.bpm.service.task.cmd;
import lombok.extern.slf4j.Slf4j;
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
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.FlowElement;
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
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务自由跳转命令
 */
@Slf4j
public class BackTaskCmd implements Command<String>, Serializable {

    @Serial
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
        if (targetActivityId == null || targetActivityId.isEmpty()) {
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
            log.warn("[BackTaskCmd] source is not a UserTask: {}", sourceActivityId);
            // 默认仅支持用户任务，可根据需要扩展
        }
        FlowNode targetFlowElement = (FlowNode) process.getFlowElement(targetActivityId, true);
        
        if (targetFlowElement == null) {
            throw new FlowableObjectNotFoundException("Target activity " + targetActivityId + " doesn't exist in process definition " + processDefinitionId, FlowNode.class);
        }
        
        log.info("[BackTaskCmd] source element: {}({}), target element: {}({})", 
                sourceFlowElement.getName(), sourceFlowElement.getId(), 
                targetFlowElement.getName(), targetFlowElement.getId());
        
        // 验证目标节点是否可达
        if (!FlowableJumpUtils.isReachable(process, targetFlowElement, sourceFlowElement)) {
            log.error("[BackTaskCmd] target activity {} is not reachable from {}", targetActivityId, sourceActivityId);
            throw new FlowableException("Cannot back to [" + targetActivityId + "]");
        }
        
        // 获取执行实体管理器，用于获取更多执行实体信息
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        
        // 检查任务是否在多实例或子流程中
        ExecutionEntity taskExecution = executionEntityManager.findById(executionId);
        if (taskExecution != null) {
            boolean isMultiInstanceRoot = taskExecution.isMultiInstanceRoot();
            boolean isInMultiInstance = FlowableJumpUtils.isExecutionInsideMultiInstance(taskExecution);
            boolean isScope = taskExecution.isScope();
            
            log.info("[BackTaskCmd] task execution attributes - id: {}, isMultiInstanceRoot: {}, isInMultiInstance: {}, isScope: {}", 
                    taskExecution.getId(), isMultiInstanceRoot, isInMultiInstance, isScope);
            
            if (isMultiInstanceRoot || isInMultiInstance) {
                log.info("[BackTaskCmd] detected multi-instance scenario");
                // 处理多实例场景的特殊逻辑
                ExecutionEntity multiInstanceRoot = executionEntityManager.findFirstMultiInstanceRoot(taskExecution);
                if (multiInstanceRoot != null) {
                    log.info("[BackTaskCmd] found multi-instance root: {}", multiInstanceRoot.getId());
                }
            }
            
            if (isScope) {
                log.info("[BackTaskCmd] detected scope scenario (possibly subprocess)");
                // 处理子流程场景的特殊逻辑
                ExecutionEntity scopeExecution = executionEntityManager.findFirstScope(taskExecution);
                if (scopeExecution != null && !scopeExecution.getId().equals(taskExecution.getId())) {
                    log.info("[BackTaskCmd] found parent scope: {}", scopeExecution.getId());
                }
            }
        }
        
        // 获取真实的源和目标活动ID
        String[] sourceAndTargetRealActivityId = FlowableJumpUtils.getSourceAndTargetRealActivityId(sourceFlowElement, targetFlowElement);
        String sourceRealActivityId = sourceAndTargetRealActivityId[0];
        String targetRealActivityId = sourceAndTargetRealActivityId[1];
        log.info("[BackTaskCmd] real source activity: {}, real target activity: {}", sourceRealActivityId, targetRealActivityId);

        // 获取特殊网关节点信息
        Map<String, Set<String>> specialGatewayNodes = FlowableJumpUtils.getSpecialGatewayElements(process);
        List<String> sourceInSpecialGatewayList = new ArrayList<>();
        List<String> targetInSpecialGatewayList = new ArrayList<>();
        setSpecialGatewayList(sourceRealActivityId, targetRealActivityId, specialGatewayNodes,
                sourceInSpecialGatewayList, targetInSpecialGatewayList);
        
        if (!sourceInSpecialGatewayList.isEmpty()) {
            log.info("[BackTaskCmd] source is in special gateways: {}", String.join(", ", sourceInSpecialGatewayList));
        }
        if (!targetInSpecialGatewayList.isEmpty()) {
            log.info("[BackTaskCmd] target is in special gateways: {}", String.join(", ", targetInSpecialGatewayList));
        }

        Set<String> sourceRealAcitivtyIds = null;
        String targetRealSpecialGateway = null;

        // 处理不同的网关场景
        if (targetInSpecialGatewayList.isEmpty() && sourceInSpecialGatewayList.isEmpty()) {
            // 简单场景：源和目标都不在特殊网关中
            sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
            log.info("[BackTaskCmd] simple case: both source and target are not in special gateways");
        } else if (targetInSpecialGatewayList.isEmpty() && !sourceInSpecialGatewayList.isEmpty()) {
            // 源在特殊网关中，目标不在
            sourceRealAcitivtyIds = specialGatewayNodes.get(sourceInSpecialGatewayList.get(0));
            log.info("[BackTaskCmd] source in gateway, target not: using source gateway nodes: {}", 
                    sourceRealAcitivtyIds.stream().collect(Collectors.joining(", ")));
        } else if (!targetInSpecialGatewayList.isEmpty() && sourceInSpecialGatewayList.isEmpty()) {
            // 源不在特殊网关中，目标在
            sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
            targetRealSpecialGateway = targetInSpecialGatewayList.get(0);
            log.info("[BackTaskCmd] target in gateway, source not: using target special gateway: {}", targetRealSpecialGateway);
        } else {
            // 源和目标都在特殊网关中，需要找到它们的公共祖先
            int diffSpecialGatewayLevel = FlowableJumpUtils.getDiffLevel(sourceInSpecialGatewayList, targetInSpecialGatewayList);
            log.info("[BackTaskCmd] both in gateways, diff level: {}", diffSpecialGatewayLevel);
            
            if (diffSpecialGatewayLevel == -1) {
                sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
                log.info("[BackTaskCmd] no common ancestor, using source activity");
            } else {
                if (sourceInSpecialGatewayList.size() == diffSpecialGatewayLevel) {
                    sourceRealAcitivtyIds = Sets.newHashSet(sourceRealActivityId);
                    log.info("[BackTaskCmd] source at diff level, using source activity");
                } else {
                    sourceRealAcitivtyIds = specialGatewayNodes.get(sourceInSpecialGatewayList.get(diffSpecialGatewayLevel));
                    log.info("[BackTaskCmd] using gateway nodes at diff level: {}", 
                            sourceRealAcitivtyIds.stream().collect(Collectors.joining(", ")));
                }
                if (targetInSpecialGatewayList.size() != diffSpecialGatewayLevel) {
                    targetRealSpecialGateway = targetInSpecialGatewayList.get(diffSpecialGatewayLevel);
                    log.info("[BackTaskCmd] using target special gateway at diff level: {}", targetRealSpecialGateway);
                }
            }
        }

        // 获取实际的执行实体
        List<ExecutionEntity> realExecutions = getRealExecutions(commandContext, processInstanceId, executionId,
                sourceRealActivityId, sourceRealAcitivtyIds);
        
        if (realExecutions.isEmpty()) {
            log.error("[BackTaskCmd] no real executions found, cannot proceed with activity change");
            throw new FlowableException("No executions found for activity change");
        }
        
        List<String> realExecutionIds = realExecutions.stream().map(ExecutionEntity::getId).collect(Collectors.toList());
        log.info("[BackTaskCmd] moving executions {} to {}", realExecutionIds, targetRealActivityId);
        
        // 执行活动状态变更
        try {
            runtimeService.createChangeActivityStateBuilder().processInstanceId(processInstanceId)
                    .moveExecutionsToSingleActivityId(realExecutionIds, targetRealActivityId).changeState();
            log.info("[BackTaskCmd] activity state changed successfully");
            
            // 如果目标在特殊网关中，创建特殊网关结束执行
            if (targetRealSpecialGateway != null) {
                log.info("[BackTaskCmd] creating special gateway end executions for: {}", targetRealSpecialGateway);
                createTargetInSpecialGatewayEndExecutions(commandContext, realExecutions, process,
                        targetInSpecialGatewayList, targetRealSpecialGateway);
            }
            
            // 检查目标节点是否已创建
            verifyTargetCreated(commandContext, processInstanceId, targetRealActivityId);
            
            return targetRealActivityId;
        } catch (Exception e) {
            log.error("[BackTaskCmd] error during activity state change", e);
            throw new FlowableException("Error during activity state change: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证目标节点是否已创建
     */
    private void verifyTargetCreated(CommandContext commandContext, String processInstanceId, String targetActivityId) {
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        List<ExecutionEntity> executions = executionEntityManager.findChildExecutionsByProcessInstanceId(processInstanceId);
        
        // 1. 首先检查是否存在目标活动的执行实体
        boolean executionFound = false;
        ExecutionEntity targetExecution = null;
        for (ExecutionEntity execution : executions) {
            if (targetActivityId.equals(execution.getActivityId())) {
                executionFound = true;
                targetExecution = execution;
                log.info("[BackTaskCmd] target node execution found: execution {} for activity {}", 
                        execution.getId(), targetActivityId);
                break;
            }
        }
        
        if (!executionFound) {
            log.warn("[BackTaskCmd] target node execution {} appears not to be created after state change", targetActivityId);
            return;
        }
        
        // 2. 检查是否存在对应的任务实体
        List<TaskEntity> tasks = CommandContextUtil.getTaskService(commandContext)
                .findTasksByProcessInstanceId(processInstanceId);
        boolean taskFound = false;
        for (TaskEntity task : tasks) {
            if (targetActivityId.equals(task.getTaskDefinitionKey())) {
                taskFound = true;
                log.info("[BackTaskCmd] target node task found: task {} for activity {}", 
                        task.getId(), targetActivityId);
                break;
            }
        }
        
        // 3. 如果执行实体存在但任务实体不存在，尝试修复
        if (executionFound && !taskFound && targetExecution != null) {
            log.warn("[BackTaskCmd] execution exists but task not found for activity {}, attempting to fix...", targetActivityId);
            
            // 获取流程定义
            Process process = ProcessDefinitionUtil.getProcess(targetExecution.getProcessDefinitionId());
            FlowElement flowElement = process.getFlowElement(targetActivityId, true);
            
            if (flowElement instanceof UserTask) {
                log.info("[BackTaskCmd] target node is a UserTask, forcing task creation");
                
                // 强制触发任务创建行为
                targetExecution.setCurrentFlowElement(flowElement);
                CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(targetExecution);
                
                log.info("[BackTaskCmd] task creation operation scheduled for target activity {}", targetActivityId);
            }
        }
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
        if (executionEntitys.isEmpty()) {
            log.warn("[BackTaskCmd] executionEntitys is empty, cannot create target gateway end executions");
            return;
        }
        
        String parentExecutionId = executionEntitys.iterator().next().getParentId();
        log.info("[BackTaskCmd] creating gateway end executions with parent execution: {}", parentExecutionId);
        
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity parentExecutionEntity = executionEntityManager.findById(parentExecutionId);
        
        if (parentExecutionEntity == null) {
            log.warn("[BackTaskCmd] parent execution entity is null, trying to find from process instance");
            // 尝试使用第一个执行实体的流程实例ID
            String processInstanceId = executionEntitys.iterator().next().getProcessInstanceId();
            parentExecutionEntity = executionEntityManager.findById(processInstanceId);
            
            if (parentExecutionEntity == null) {
                log.error("[BackTaskCmd] unable to find parent execution, gateway end executions cannot be created");
                return;
            }
            log.info("[BackTaskCmd] using process instance execution as parent: {}", parentExecutionEntity.getId());
        }

        // 检查父执行实体是否为多实例或子流程
        ExecutionEntity multiInstanceRoot = null;
        ExecutionEntity scopeExecution = null;
        
        // 获取执行实体的所有信息用于调试
        String activityId = parentExecutionEntity.getActivityId();
        boolean isScope = parentExecutionEntity.isScope();
        boolean isMultiInstanceRoot = parentExecutionEntity.isMultiInstanceRoot();
        
        log.info("[BackTaskCmd] parent execution info - id: {}, activityId: {}, isScope: {}, isMultiInstanceRoot: {}", 
                parentExecutionEntity.getId(), activityId, isScope, isMultiInstanceRoot);
        
        // 处理多实例情况
        if (isMultiInstanceRoot) {
            multiInstanceRoot = parentExecutionEntity;
            log.info("[BackTaskCmd] parent execution is a multi-instance root");
            
            // 获取多实例根的父执行实体，用于创建网关执行
            ExecutionEntity multiInstanceParent = executionEntityManager.findById(parentExecutionEntity.getParentId());
            if (multiInstanceParent != null) {
                parentExecutionEntity = multiInstanceParent;
                log.info("[BackTaskCmd] using multi-instance root's parent: {}", parentExecutionEntity.getId());
            }
        }
        
        // 处理子流程情况
        if (isScope) {
            scopeExecution = parentExecutionEntity;
            log.info("[BackTaskCmd] parent execution is a scope (possibly subprocess)");
            
            // 对于子流程，我们可能需要找到父级执行实体
            ExecutionEntity parentScopeExecution = executionEntityManager.findFirstScope(parentExecutionEntity);
            if (parentScopeExecution != null && !parentScopeExecution.getId().equals(parentExecutionEntity.getId())) {
                parentExecutionEntity = parentScopeExecution;
                log.info("[BackTaskCmd] using parent scope execution: {}", parentExecutionEntity.getId());
            }
        }

        int index = targetInSpecialGatewayList.indexOf(targetRealSpecialGateway);
        log.info("[BackTaskCmd] target gateway index: {} in list of size: {}", index, targetInSpecialGatewayList.size());
        
        // 记录创建的网关执行实体，用于后续处理
        List<ExecutionEntity> createdGatewayExecutions = new ArrayList<>();
        
        for (; index < targetInSpecialGatewayList.size(); index++) {
            String targetInSpecialGateway = targetInSpecialGatewayList.get(index);
            log.info("[BackTaskCmd] processing target special gateway: {}", targetInSpecialGateway);
            
            // 尝试查找END后缀的网关节点
            String targetInSpecialGatewayEndId = targetInSpecialGateway + BpmnModelConstants.SPECIAL_GATEWAY_END_SUFFIX;
            FlowNode targetInSpecialGatewayEnd = (FlowNode) process.getFlowElement(targetInSpecialGatewayEndId, true);
            
            // 如果找不到END后缀的网关节点，尝试查找JOIN后缀的网关节点
            if (targetInSpecialGatewayEnd == null) {
                targetInSpecialGatewayEndId = targetInSpecialGateway + BpmnModelConstants.SPECIAL_GATEWAY_JOIN_SUFFIX;
                targetInSpecialGatewayEnd = (FlowNode) process.getFlowElement(targetInSpecialGatewayEndId, true);
                log.info("[BackTaskCmd] using JOIN suffix gateway: {}", targetInSpecialGatewayEndId);
            } else {
                log.info("[BackTaskCmd] using END suffix gateway: {}", targetInSpecialGatewayEndId);
            }
            
            // 如果找不到任何网关节点，跳过此次循环
            if (targetInSpecialGatewayEnd == null) {
                log.warn("[BackTaskCmd] cannot find target gateway end for: {}, skipping", targetInSpecialGateway);
                continue;
            }
            
            int nbrOfExecutionsToJoin = targetInSpecialGatewayEnd.getIncomingFlows().size();
            log.info("[BackTaskCmd] gateway has {} incoming flows, creating {} child executions", 
                    nbrOfExecutionsToJoin, nbrOfExecutionsToJoin - 1);
            
            // 检查是否已经存在执行实体，避免重复创建
            List<ExecutionEntity> existingExecutions = executionEntityManager.findChildExecutionsByParentExecutionId(parentExecutionEntity.getId());
            Set<String> existingActivityIds = existingExecutions.stream()
                    .map(ExecutionEntity::getActivityId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            if (existingActivityIds.contains(targetInSpecialGatewayEnd.getId())) {
                log.info("[BackTaskCmd] execution for gateway {} already exists, skipping creation", targetInSpecialGatewayEnd.getId());
                continue;
            }
            
            try {
                // 创建所需数量的子执行实体
                for (int i = 0; i < nbrOfExecutionsToJoin - 1; i++) {
                    ExecutionEntity childExecution;
                    
                    // 根据多实例和子流程情况决定如何创建子执行实体
                    if (multiInstanceRoot != null) {
                        // 对于多实例，我们可能需要特殊处理
                        log.info("[BackTaskCmd] creating execution for multi-instance scenario");
                        childExecution = executionEntityManager.createChildExecution(parentExecutionEntity);
                    } else if (scopeExecution != null) {
                        // 对于子流程，确保在正确的作用域中创建
                        log.info("[BackTaskCmd] creating execution for subprocess scenario");
                        childExecution = executionEntityManager.createChildExecution(parentExecutionEntity);
                    } else {
                        // 标准情况
                        childExecution = executionEntityManager.createChildExecution(parentExecutionEntity);
                    }
                    
                    childExecution.setCurrentFlowElement(targetInSpecialGatewayEnd);
                    log.info("[BackTaskCmd] created child execution: {} for gateway: {}", 
                            childExecution.getId(), targetInSpecialGatewayEnd.getId());
                    
                    ActivityBehavior activityBehavior = (ActivityBehavior) targetInSpecialGatewayEnd.getBehavior();
                    if (activityBehavior != null) {
                        log.info("[BackTaskCmd] executing behavior for child execution: {}", childExecution.getId());
                        activityBehavior.execute(childExecution);
                        createdGatewayExecutions.add(childExecution);
                    } else {
                        log.warn("[BackTaskCmd] no activity behavior found for gateway: {}", targetInSpecialGatewayEnd.getId());
                    }
                }
            } catch (Exception e) {
                log.error("[BackTaskCmd] error creating gateway end executions", e);
            }
        }
        
        // 特殊处理包容网关后的流程节点
        // 当所有网关执行完成后，我们需要检查是否有网关之后的节点需要执行
        if (!createdGatewayExecutions.isEmpty()) {
            log.info("[BackTaskCmd] processing downstream nodes after inclusive gateway");
            
            String processInstanceId = parentExecutionEntity.getProcessInstanceId();
            List<ExecutionEntity> allExecutions = executionEntityManager.findChildExecutionsByProcessInstanceId(processInstanceId);
            
            // 获取当前流程实例中的所有UserTask节点
            List<UserTask> userTasks = new ArrayList<>();
            for (FlowElement element : process.getFlowElements()) {
                if (element instanceof UserTask) {
                    userTasks.add((UserTask) element);
                }
            }
            
            // 检查是否有需要激活的UserTask节点
            for (UserTask userTask : userTasks) {
                // 跳过已经有执行实体的节点
                boolean hasExecution = false;
                for (ExecutionEntity execution : allExecutions) {
                    if (userTask.getId().equals(execution.getActivityId())) {
                        hasExecution = true;
                        break;
                    }
                }
                
                if (!hasExecution) {
                    // 如果是包容网关之后的节点，尝试创建执行实体
                    log.info("[BackTaskCmd] checking if UserTask {} needs to be activated", userTask.getId());
                    
                    // 这里可以添加更多的逻辑来判断是否需要激活特定的UserTask
                    // 例如，检查是否是目标网关的后续节点
                }
            }
        }
    }

    private List<ExecutionEntity> getRealExecutions(CommandContext commandContext, String processInstanceId,
                                                    String taskExecutionId, String sourceRealActivityId,
                                                    Set<String> activityIds) {
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity taskExecution = executionEntityManager.findById(taskExecutionId);
        
        if (taskExecution == null) {
            log.warn("[BackTaskCmd] taskExecution is null for taskExecutionId: {}", taskExecutionId);
            // 尝试从流程实例中查找活动的执行实体
            List<ExecutionEntity> processExecutions = executionEntityManager.findChildExecutionsByProcessInstanceId(processInstanceId);
            if (!processExecutions.isEmpty()) {
                taskExecution = processExecutions.get(0);
                log.info("[BackTaskCmd] using first process execution as taskExecution: {}", taskExecution.getId());
            } else {
                throw new FlowableException("No execution found for process instance: " + processInstanceId);
            }
        }
        
        // 检查任务执行是否在多实例或子流程中
        ExecutionEntity scopeExecution = executionEntityManager.findFirstScope(taskExecution);
        ExecutionEntity multiInstanceRoot = executionEntityManager.findFirstMultiInstanceRoot(taskExecution);
        
        if (multiInstanceRoot != null) {
            log.info("[BackTaskCmd] task execution is part of multi-instance activity: {}", multiInstanceRoot.getActivityId());
        }
        
        if (scopeExecution != null && !scopeExecution.getId().equals(taskExecution.getId())) {
            log.info("[BackTaskCmd] task execution is within scope: {}", scopeExecution.getActivityId());
        }
        
        List<ExecutionEntity> executions = executionEntityManager.findChildExecutionsByProcessInstanceId(processInstanceId);
        log.debug("[BackTaskCmd] found {} child executions for process instance {}", executions.size(), processInstanceId);
        
        Set<String> parentExecutionIds = FlowableJumpUtils.getParentExecutionIdsByActivityId(executions, sourceRealActivityId);
        log.debug("[BackTaskCmd] found {} parent execution IDs for source activity {}: {}", 
                  parentExecutionIds.size(), sourceRealActivityId, parentExecutionIds);
        
        String realParentExecutionId;
        try {
            realParentExecutionId = FlowableJumpUtils.getParentExecutionIdFromParentIds(taskExecution, parentExecutionIds);
            log.info("[BackTaskCmd] found real parent execution ID: {}", realParentExecutionId);
        } catch (FlowableException e) {
            log.warn("[BackTaskCmd] parent execution not found for {} using task parent {}: {}", 
                     taskExecutionId, taskExecution.getParentId(), e.getMessage());
            
            // 如果找不到父执行ID，尝试使用作用域执行实体
            if (scopeExecution != null) {
                realParentExecutionId = scopeExecution.getId();
                log.info("[BackTaskCmd] using scope execution as parent: {}", realParentExecutionId);
            } else if (multiInstanceRoot != null) {
                // 如果有多实例根执行实体，使用它的父执行ID
                realParentExecutionId = multiInstanceRoot.getParentId();
                log.info("[BackTaskCmd] using multi-instance root's parent as parent: {}", realParentExecutionId);
            } else {
                realParentExecutionId = taskExecution.getParentId();
                
                // 如果parent为空（可能是根执行），则使用流程实例ID
                if (realParentExecutionId == null) {
                    realParentExecutionId = processInstanceId;
                    log.info("[BackTaskCmd] using process instance ID as parent: {}", realParentExecutionId);
                }
            }
        }
        
        // 优先尝试通过父执行ID和活动ID查找
        List<ExecutionEntity> realExecutions = executionEntityManager.findExecutionsByParentExecutionAndActivityIds(
                realParentExecutionId, activityIds);
        
        // 如果没有找到匹配的执行实体，尝试直接查找活动ID
        if (realExecutions.isEmpty()) {
            log.info("[BackTaskCmd] no executions found by parent and activity IDs, trying direct activity search");
            for (ExecutionEntity execution : executions) {
                if (activityIds.contains(execution.getActivityId())) {
                    realExecutions.add(execution);
                    log.info("[BackTaskCmd] found execution by direct activity match: {}", execution.getId());
                }
            }
        }
        
        // 如果仍然没有找到，检查多实例情况
        if (realExecutions.isEmpty() && multiInstanceRoot != null) {
            log.info("[BackTaskCmd] trying to find executions from multi-instance root");
            // 获取多实例的所有相关执行实体
            List<ExecutionEntity> multiInstanceExecutions = executionEntityManager.collectChildren(multiInstanceRoot);
            for (ExecutionEntity execution : multiInstanceExecutions) {
                if (activityIds.contains(execution.getActivityId())) {
                    realExecutions.add(execution);
                    log.info("[BackTaskCmd] found execution from multi-instance: {}", execution.getId());
                }
            }
        }
        
        // 处理包容网关场景：如果源活动是包容网关中的活动，可能需要特殊处理
        if (realExecutions.isEmpty()) {
            log.info("[BackTaskCmd] checking for inclusive gateway scenario");
            
            // 尝试查找任何与该流程实例相关的执行实体
            for (ExecutionEntity execution : executions) {
                // 如果找到任何活动执行实体，将其作为回退选项
                if (execution.isActive() && execution.getActivityId() != null) {
                    log.info("[BackTaskCmd] found active execution {} with activity {}", 
                            execution.getId(), execution.getActivityId());
                    realExecutions.add(execution);
                    break;
                }
            }
        }
        
        // 最后，如果其他方法都失败，使用当前任务的执行
        if (realExecutions.isEmpty()) {
            log.info("[BackTaskCmd] no executions found, fallback to current execution {}", taskExecutionId);
            realExecutions = Collections.singletonList(taskExecution);
        }
        
        log.info("[BackTaskCmd] returning {} real executions: {}", 
                realExecutions.size(), 
                realExecutions.stream().map(ExecutionEntity::getId).collect(Collectors.joining(", ")));
        
        return realExecutions;
    }
}