package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.*;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmTaskConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import cn.iocoder.yudao.module.bpm.enums.definition.*;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmReasonEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskSignTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.yudao.module.bpm.service.task.cmd.BackTaskCmd;
import cn.iocoder.yudao.module.bpm.service.task.cmd.DeleteTaskCmd;
import cn.iocoder.yudao.module.bpm.service.task.dto.BpmMultiInstanceMessageDTO;
import cn.iocoder.yudao.module.bpm.service.worktime.BpmWorkTimeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.START_USER_NODE_ID;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_RETURN_FLAG;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TIMEOUT_REMIND_COUNT;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.*;

/**
 * 流程任务实例 Service 实现类
 *
 * @author 芋道源码
 * @author jason
 */
@Slf4j
@Service
public class BpmTaskServiceImpl implements BpmTaskService {

    @Resource
    private TaskService taskService;
    @Resource
    private HistoryService historyService;
    @Resource
    private RuntimeService runtimeService;
    @Resource
    private ManagementService managementService;

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;
    @Resource
    private BpmModelService modelService;
    @Resource
    private BpmMessageService messageService;
    @Resource
    private BpmFormService formService;
    @Resource
    private BpmWorkTimeService workTimeService;
    @Resource
    private BpmTaskTransferConfigService taskTransferConfigService;
    @Resource
    private BpmProcessInstanceCommentService commentService;
    /**
     * 任务执行器，用于在事务提交后异步处理自动审批等逻辑，避免与流程引擎的事务冲突
     */
    @Resource(name = "applicationTaskExecutor")
    private AsyncTaskExecutor taskExecutor;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    private static final DateTimeFormatter LOG_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND);

    // ========== Query 查询相关方法 ==========

    @Override
    public PageResult<Task> getTaskTodoPage(Long userId, BpmTaskPageReqVO pageVO) {
        // 创建 OR 条件查询
        TaskQuery taskQuery = taskService.createTaskQuery()
                .or()
                .taskAssignee(String.valueOf(userId)) // 分配给自己的任务
                .endOr()
                .active()
                .includeProcessVariables()
                .orderByTaskCreateTime().desc(); // 创建时间倒序

        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageVO.getCategory())) {
            taskQuery.taskCategory(pageVO.getCategory());
        }
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            // 使用LocalDateTimeUtils.parse()将String转为LocalDateTime
            LocalDateTime startTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[0]);
            LocalDateTime endTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[1]);
            taskQuery.taskCreatedAfter(DateUtils.of(startTime));
            taskQuery.taskCreatedBefore(DateUtils.of(endTime));
        }
        // 发起人的条件判断
        if (ArrayUtil.isNotEmpty(pageVO.getStartUserId())) {
            taskQuery.processVariableValueEquals(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, pageVO.getStartUserId());
        }
        // 支持通过流程实例名称（NAME_字段）模糊查询
        if (StrUtil.isNotEmpty(pageVO.getProcessInstanceName())) {
            // 1. 先查出所有匹配的流程实例ID
            System.out.println(111111212);
            List<String> matchedProcessInstanceIds = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceNameLike("%" + pageVO.getProcessInstanceName() + "%")
                    .list()
                    .stream()
                    .map(HistoricProcessInstance::getId)
                    .toList();
            // 2. 如果没有匹配的流程实例，直接返回空
            if (CollUtil.isEmpty(matchedProcessInstanceIds)) {
                return PageResult.empty();
            }
            // 3. 用流程实例ID过滤任务
            taskQuery.processInstanceIdIn(matchedProcessInstanceIds);
        }
        long count = taskQuery.count();
        if (count == 0) {
            return PageResult.empty();
        }
        List<Task> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        return new PageResult<>(tasks, count);
    }

    @Override
    public BpmTaskRespVO getFirstTodoTask(Long userId, String processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        // 1. 查询所有任务
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .processInstanceId(processInstanceId)
                .includeTaskLocalVariables()
                .includeProcessVariables()
                .orderByTaskCreateTime().asc() // 按创建时间升序
                .list();
        if (CollUtil.isEmpty(tasks)) {
            return null;
        }

        // 2.1 查询我的首个任务
        Task todoTask = CollUtil.findOne(tasks, task -> {
            return isAssignUserTask(userId, task) // 当前用户为审批人
                    || isAddSignUserTask(userId, task); // 当前用户为加签人（为了减签）
        });
        if (todoTask == null) {
            return null;
        }
        // 2.2 查询该任务的子任务
        List<Task> childrenTasks = getAllChildrenTaskListByParentTaskId(todoTask.getId(), tasks);

        // 3. 转换返回
        BpmnModel bpmnModel = bpmProcessDefinitionService.getProcessDefinitionBpmnModel(todoTask.getProcessDefinitionId());
        Map<Integer, BpmTaskRespVO.OperationButtonSetting> buttonsSetting = BpmnModelUtils.parseButtonsSetting(
                bpmnModel, todoTask.getTaskDefinitionKey());
        Boolean signEnable = parseSignEnable(bpmnModel, todoTask.getTaskDefinitionKey());
        Boolean reasonRequire = parseReasonRequire(bpmnModel, todoTask.getTaskDefinitionKey());
        Integer nodeType = parseNodeType(BpmnModelUtils.getFlowElementById(bpmnModel, todoTask.getTaskDefinitionKey()));

        // 4. 任务表单
        BpmFormDO taskForm = null;
        if (StrUtil.isNotBlank(todoTask.getFormKey())) {
            taskForm = formService.getForm(NumberUtils.parseLong(todoTask.getFormKey()));
        }

        return BpmTaskConvert.INSTANCE.buildTodoTask(todoTask, childrenTasks, buttonsSetting, taskForm)
                .setNodeType(nodeType).setSignEnable(signEnable).setReasonRequire(reasonRequire);
    }

    @Override
    public PageResult<HistoricTaskInstance> getTaskDonePage(Long userId, BpmTaskPageReqVO pageVO) {
        // 1. 构建查询条件
        HistoricTaskInstanceQuery taskQuery = buildHistoricTaskQuery(userId, pageVO);

        // 2. 获取准确的总数（通过查询所有任务ID并过滤）
        // 创建一个仅查询ID的查询对象，配置与主查询相同的过滤条件
        HistoricTaskInstanceQuery countQuery = buildHistoricTaskQuery(userId, pageVO);
        long total = countQuery.list().stream()
                .filter(task -> !START_USER_NODE_ID.equals(task.getTaskDefinitionKey()))
                .count();

        // 3. 如果没有数据，直接返回空结果
        if (total == 0) {
            return PageResult.empty();
        }

        // 4. 分页查询数据
        List<HistoricTaskInstance> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());

        // 5. 特殊：强制移除自动完成的"发起人"节点
        // 补充说明：由于 taskQuery 无法方面的过滤，所以暂时通过内存过滤
        tasks.removeIf(task -> task.getTaskDefinitionKey().equals(START_USER_NODE_ID));

        // 6. 返回准确的分页结果
        return new PageResult<>(tasks, total);
    }

    /**
     * 构建历史任务查询对象，用于代码复用
     */
    private HistoricTaskInstanceQuery buildHistoricTaskQuery(Long userId, BpmTaskPageReqVO pageVO) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .finished() // 已完成
                .taskAssignee(String.valueOf(userId)) // 分配给自己
                .includeTaskLocalVariables()
                .orderByHistoricTaskInstanceEndTime().desc(); // 审批时间倒序

        if (StrUtil.isNotBlank(pageVO.getName())) {
            query.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            // 使用LocalDateTimeUtils.parse()将String转为LocalDateTime
            LocalDateTime startTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[0]);
            LocalDateTime endTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[1]);
            query.taskCreatedAfter(DateUtils.of(startTime));
            query.taskCreatedBefore(DateUtils.of(endTime));
        }
        return query;
    }

    @Override
    public PageResult<HistoricTaskInstance> getTaskPage(Long userId, BpmTaskPageReqVO pageVO) {
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
                .includeTaskLocalVariables()
                .taskTenantId(FlowableUtils.getTenantId())
                .orderByHistoricTaskInstanceEndTime().desc(); // 审批时间倒序
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageVO.getCategory())) {
            taskQuery.taskCategory(pageVO.getCategory());
        }
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            // 使用LocalDateTimeUtils.parse()将String转为LocalDateTime
            LocalDateTime startTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[0]);
            LocalDateTime endTime = LocalDateTimeUtils.parse(pageVO.getCreateTime()[1]);
            taskQuery.taskCreatedAfter(DateUtils.of(startTime));
            taskQuery.taskCreatedBefore(DateUtils.of(endTime));
        }
        // 执行查询
        long count = taskQuery.count();
        if (count == 0) {
            return PageResult.empty();
        }
        List<HistoricTaskInstance> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        return new PageResult<>(tasks, count);
    }

    @Override
    public List<Task> getTasksByProcessInstanceIds(List<String> processInstanceIds) {
        if (CollUtil.isEmpty(processInstanceIds)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).list();
    }

    @Override
    public List<HistoricTaskInstance> getTaskListByProcessInstanceId(String processInstanceId, Boolean asc) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .includeTaskLocalVariables()
                .processInstanceId(processInstanceId);
        if (Boolean.TRUE.equals(asc)) {
            query.orderByHistoricTaskInstanceStartTime().asc();
        } else {
            query.orderByHistoricTaskInstanceStartTime().desc();
        }
        return query.list();
    }

    /**
     * 校验任务是否存在，并且是否是分配给自己的任务
     *
     * @param userId 用户 id
     * @param taskId task id
     */
    private Task validateTask(Long userId, String taskId, Boolean managerial) {
        Task task = validateTaskExist(taskId);
        // 如果不是管理员操作，则需要验证任务是否分配给当前用户
        if (Boolean.FALSE.equals(managerial)) {
            // 为什么判断 assignee 非空的情况下？
            // 例如说：在审批人为空时，我们会有"自动审批通过"的策略，此时 userId 为 null，允许通过
            if (StrUtil.isNotBlank(task.getAssignee())
                    && ObjectUtil.notEqual(userId, NumberUtils.parseLong(task.getAssignee()))) {
                throw exception(TASK_OPERATE_FAIL_ASSIGN_NOT_SELF);
            }
        }
        return task;
    }

    private Task validateTaskExist(String id) {
        Task task = getTask(id);
        if (task == null) {
            throw exception(TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public Task getTask(String id) {
        return taskService.createTaskQuery().taskId(id).includeTaskLocalVariables().singleResult();
    }

    @Override
    public HistoricTaskInstance getHistoricTask(String id) {
        return historyService.createHistoricTaskInstanceQuery().taskId(id).includeTaskLocalVariables().singleResult();
    }

    @Override
    public List<HistoricTaskInstance> getHistoricTasks(Collection<String> taskIds) {
        return historyService.createHistoricTaskInstanceQuery().taskIds(taskIds).includeTaskLocalVariables().list();
    }

    @Override
    public List<Task> getRunningTaskListByProcessInstanceId(String processInstanceId, Boolean assigned, String defineKey) {
        Assert.notNull(processInstanceId, "processInstanceId 不能为空");
        TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(processInstanceId).active()
                .includeTaskLocalVariables();
        if (BooleanUtil.isTrue(assigned)) {
            taskQuery.taskAssigned();
        } else if (BooleanUtil.isFalse(assigned)) {
            taskQuery.taskUnassigned();
        }
        if (StrUtil.isNotEmpty(defineKey)) {
            taskQuery.taskDefinitionKey(defineKey);
        }
        return taskQuery.list();
    }

    @Override
    public List<UserTask> getUserTaskListByReturn(String id) {
        // 1.1 校验当前任务 task 存在
        Task task = validateTaskExist(id);
        // 1.2 根据流程定义获取流程模型信息
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        FlowElement source = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        if (source == null) {
            throw exception(TASK_NOT_EXISTS);
        }

        // 2.1 查询该任务的前置任务节点的 key 集合
        List<UserTask> previousUserList = BpmnModelUtils.getPreviousUserTaskList(source, null, null);
        if (CollUtil.isEmpty(previousUserList)) {
            return Collections.emptyList();
        }

        // 2.2 如果当前节点在并行网关内,需要特殊处理
        if (isInParallelGateway(source)) {
            // 获取并行/包容网关节点
            Gateway parallelGateway = getParentParallelGateway(source);
            if (parallelGateway != null) {
                // 获取并行网关前的所有用户任务节点
                List<UserTask> gatewayPreviousList = BpmnModelUtils.getPreviousUserTaskList(parallelGateway, null, null);
                if (CollUtil.isNotEmpty(gatewayPreviousList)) {
                    previousUserList.addAll(gatewayPreviousList);
                }
            }
        }

        // 2.3 过滤重复节点
        previousUserList = CollUtil.distinct(previousUserList);

        return previousUserList;
    }

    /**
     * 判断节点是否在并行网关或包容网关内
     *
     * @param element 当前节点元素
     * @return 是否在并行网关内
     */
    private boolean isInParallelGateway(FlowElement element) {
        if (element == null) {
            log.debug("[isInParallelGateway] element is null, returning false");
            return false;
        }

        // 获取当前节点所在的流程定义
        FlowElementsContainer container = element.getParentContainer();
        if (container == null) {
            log.debug("[isInParallelGateway] container is null, returning false");
            return false;
        }

        // 如果当前节点不是UserTask，直接返回false
        if (!(element instanceof UserTask)) {
            log.debug("[isInParallelGateway] element is not a UserTask, returning false");
            return false;
        }

        // 获取当前节点的所有入口连线
        List<SequenceFlow> incomingFlows = ((UserTask) element).getIncomingFlows();
        if (CollUtil.isEmpty(incomingFlows)) {
            log.debug("[isInParallelGateway] incomingFlows is empty, returning false");
            return false;
        }

        // 查找当前节点的所有前置节点
        Set<String> sourceNodeIds = incomingFlows.stream()
                .map(SequenceFlow::getSourceRef)
                .collect(Collectors.toSet());

        // 获取所有并行网关和包容网关
        List<Gateway> gateways = new ArrayList<>();
        for (FlowElement flowElement : container.getFlowElements()) {
            if (flowElement instanceof ParallelGateway || flowElement instanceof InclusiveGateway) {
                gateways.add((Gateway) flowElement);
            }
        }

        if (CollUtil.isEmpty(gateways)) {
            log.debug("[isInParallelGateway] No gateways found, returning false");
            return false;
        }

        // 查找分支网关（有多个出口的网关）
        List<Gateway> forkGateways = gateways.stream()
                .filter(gateway -> gateway.getOutgoingFlows().size() > 1)
                .collect(Collectors.toList());

        // 查找合并网关（有多个入口的网关）
        List<Gateway> joinGateways = gateways.stream()
                .filter(gateway -> gateway.getIncomingFlows().size() > 1)
                .collect(Collectors.toList());

        // 检查当前节点是否直接连接到合并网关
        for (Gateway joinGateway : joinGateways) {
            for (SequenceFlow flow : joinGateway.getIncomingFlows()) {
                if (flow.getSourceRef().equals(element.getId())) {
                    log.debug("[isInParallelGateway] Node directly connects to join gateway, returning true");
                    return true;
                }
            }
        }

        // 检查当前节点是否在分支网关之后、合并网关之前
        for (Gateway forkGateway : forkGateways) {
            // 找到对应的合并网关
            Gateway joinGateway = findMatchingJoinGateway(forkGateway, joinGateways);
            if (joinGateway == null) {
                continue;
            }

            // 检查当前节点是否在分支路径上
            boolean inPath = isNodeInGatewayPath(element.getId(), forkGateway.getId(), joinGateway.getId(), container.getFlowElements());

            if (inPath) {
                log.debug("[isInParallelGateway] Node is in gateway path, returning true");
                return true;
            }
        }

        log.debug("[isInParallelGateway] Node is not in any parallel gateway, returning false");
        return false;
    }

    /**
     * 查找与分支网关匹配的合并网关
     */
    private Gateway findMatchingJoinGateway(Gateway forkGateway, List<Gateway> joinGateways) {
        // 同类型的网关才能匹配
        return joinGateways.stream()
                .filter(gateway -> gateway.getClass().equals(forkGateway.getClass()))
                .filter(gateway -> !gateway.getId().equals(forkGateway.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断节点是否在网关路径上
     */
    private boolean isNodeInGatewayPath(String nodeId, String forkGatewayId, String joinGatewayId,
                                        Collection<FlowElement> flowElements) {
        log.info("[isNodeInGatewayPath] Checking if node {} is in path between {} and {}",
                nodeId, forkGatewayId, joinGatewayId);

        // 构建节点关系图
        Map<String, Set<String>> graph = new HashMap<>();
        for (FlowElement element : flowElements) {
            if (element instanceof FlowNode) {
                FlowNode flowNode = (FlowNode) element;
                Set<String> targets = new HashSet<>();

                for (SequenceFlow flow : flowNode.getOutgoingFlows()) {
                    targets.add(flow.getTargetRef());
                }

                graph.put(flowNode.getId(), targets);
            }
        }

        log.info("[isNodeInGatewayPath] Built graph with {} nodes", graph.size());

        // 使用BFS检查节点是否在分支网关和合并网关之间
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(forkGatewayId);
        visited.add(forkGatewayId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            log.info("[isNodeInGatewayPath] Visiting node: {}", currentId);

            // 如果到达合并网关，停止搜索这条路径
            if (currentId.equals(joinGatewayId)) {
                log.info("[isNodeInGatewayPath] Reached join gateway, skipping this path");
                continue;
            }

            // 如果找到目标节点，返回true
            if (currentId.equals(nodeId)) {
                log.info("[isNodeInGatewayPath] Found target node, returning true");
                return true;
            }

            // 继续搜索下一级节点
            Set<String> nextNodes = graph.getOrDefault(currentId, Collections.emptySet());
            log.info("[isNodeInGatewayPath] Next nodes from {}: {}", currentId, nextNodes);

            for (String nextNode : nextNodes) {
                if (!visited.contains(nextNode)) {
                    visited.add(nextNode);
                    queue.add(nextNode);
                    log.info("[isNodeInGatewayPath] Adding to queue: {}", nextNode);
                }
            }
        }

        log.info("[isNodeInGatewayPath] Target node not found in path, returning false");
        return false;
    }

    /**
     * 获取节点所属的父级并行网关
     */
    private Gateway getParentParallelGateway(FlowElement element) {
        // 1. 获取所有父级元素
        FlowElementsContainer parent = element.getParentContainer();
        Collection<FlowElement> flowElements = parent.getFlowElements();

        // 2. 在同级元素中查找并行网关
        for (FlowElement flowElement : flowElements) {
            if (flowElement instanceof ParallelGateway || flowElement instanceof InclusiveGateway) {
                // 检查当前节点是否属于这个并行/包容网关
                Collection<SequenceFlow> outgoingFlows = ((Gateway) flowElement).getOutgoingFlows();
                for (SequenceFlow flow : outgoingFlows) {
                    if (isFlowElementInPath(flow, element)) {
                        return (Gateway) flowElement;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 判断某个节点是否在指定连线的路径上
     */
    private boolean isFlowElementInPath(SequenceFlow flow, FlowElement target) {
        FlowElement element = flow.getTargetFlowElement();
        if (element == null) {
            return false;
        }
        if (element.equals(target)) {
            return true;
        }
        if (element instanceof FlowNode) {
            for (SequenceFlow outgoing : ((FlowNode) element).getOutgoingFlows()) {
                if (isFlowElementInPath(outgoing, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断目标节点是否在当前并行/包容分支中
     */
    private boolean isTargetInSameParallelBranch(FlowElement current, FlowElement target, BpmnModel bpmnModel) {
        Gateway forkGateway = getParentParallelGateway(current);
        if (forkGateway == null) {
            return false;
        }

        FlowElementsContainer container = current.getParentContainer();
        Collection<FlowElement> flowElements = container.getFlowElements();

        // 查找匹配的合并网关
        List<Gateway> gateways = new ArrayList<>();
        for (FlowElement element : flowElements) {
            if (element instanceof ParallelGateway || element instanceof InclusiveGateway) {
                gateways.add((Gateway) element);
            }
        }

        List<Gateway> joinGateways = gateways.stream()
                .filter(g -> g.getIncomingFlows().size() > 1)
                .collect(Collectors.toList());
        Gateway joinGateway = findMatchingJoinGateway(forkGateway, joinGateways);
        if (joinGateway == null) {
            return false;
        }

        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowElement fe : flowElements) {
            if (fe instanceof FlowNode) {
                nodeMap.put(fe.getId(), (FlowNode) fe);
            }
        }

        for (SequenceFlow flow : forkGateway.getOutgoingFlows()) {
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            String startId = flow.getTargetRef();
            visited.add(startId);
            queue.add(startId);

            boolean containsCurrent = false;
            boolean containsTarget = false;

            while (!queue.isEmpty()) {
                String nodeId = queue.poll();
                if (nodeId.equals(joinGateway.getId())) {
                    continue;
                }
                if (nodeId.equals(current.getId())) {
                    containsCurrent = true;
                }
                if (nodeId.equals(target.getId())) {
                    containsTarget = true;
                }
                FlowNode node = nodeMap.get(nodeId);
                if (node != null) {
                    for (SequenceFlow out : node.getOutgoingFlows()) {
                        String next = out.getTargetRef();
                        if (!visited.contains(next)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }

            if (containsCurrent) {
                return containsTarget;
            }
        }

        return false;
    }

    @Override
    public <T extends TaskInfo> List<T> getAllChildrenTaskListByParentTaskId(String parentTaskId, List<T> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        Map<String, List<T>> parentTaskMap = convertMultiMap(
                filterList(tasks, task -> StrUtil.isNotEmpty(task.getParentTaskId())), TaskInfo::getParentTaskId);
        if (CollUtil.isEmpty(parentTaskMap)) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>();
        // 1. 递归获取子级
        Stack<String> stack = new Stack<>();
        stack.push(parentTaskId);
        // 2. 递归遍历
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            if (stack.isEmpty()) {
                break;
            }
            // 2.1 获取子任务们
            String taskId = stack.pop();
            List<T> childTaskList = filterList(tasks, task -> StrUtil.equals(task.getParentTaskId(), taskId));
            // 2.2 如果非空，则添加到 stack 进一步递归
            if (CollUtil.isNotEmpty(childTaskList)) {
                stack.addAll(convertList(childTaskList, TaskInfo::getId));
                result.addAll(childTaskList);
            }
        }
        return result;
    }

    /**
     * 获得所有子任务列表
     *
     * @param parentTask 父任务
     * @return 所有子任务列表
     */
    private List<Task> getAllChildTaskList(Task parentTask) {
        List<Task> result = new ArrayList<>();
        // 1. 递归获取子级
        Stack<Task> stack = new Stack<>();
        stack.push(parentTask);
        // 2. 递归遍历
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            if (stack.isEmpty()) {
                break;
            }
            // 2.1 获取子任务们
            Task task = stack.pop();
            List<Task> childTaskList = getTaskListByParentTaskId(task.getId());
            // 2.2 如果非空，则添加到 stack 进一步递归
            if (CollUtil.isNotEmpty(childTaskList)) {
                stack.addAll(childTaskList);
                result.addAll(childTaskList);
            }
        }
        return result;
    }

    @Override
    public List<Task> getTaskListByParentTaskId(String parentTaskId) {
        String tableName = managementService.getTableName(TaskEntity.class);
        // taskService.createTaskQuery() 没有 parentId 参数，所以写 sql 查询
        String sql = "select ID_,NAME_,OWNER_,ASSIGNEE_ from " + tableName + " where PARENT_TASK_ID_=#{parentTaskId}";
        return taskService.createNativeTaskQuery().sql(sql).parameter("parentTaskId", parentTaskId).list();
    }

    /**
     * 获取子任务个数
     *
     * @param parentTaskId 父任务 ID
     * @return 剩余子任务个数
     */
    private Long getTaskCountByParentTaskId(String parentTaskId) {
        String tableName = managementService.getTableName(TaskEntity.class);
        String sql = "SELECT COUNT(1) from " + tableName + " WHERE PARENT_TASK_ID_=#{parentTaskId}";
        return taskService.createNativeTaskQuery().sql(sql).parameter("parentTaskId", parentTaskId).count();
    }

    /**
     * 获得任务根任务的父任务编号
     *
     * @param task 任务
     * @return 根任务的父任务编号
     */
    private String getTaskRootParentId(Task task) {
        if (task == null || task.getParentTaskId() == null) {
            return null;
        }
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            Task parentTask = getTask(task.getParentTaskId());
            if (parentTask == null) {
                return null;
            }
            if (parentTask.getParentTaskId() == null) {
                return parentTask.getId();
            }
            task = parentTask;
        }
        throw new IllegalArgumentException(String.format("Task(%s) 层级过深，无法获取父节点编号", task.getId()));
    }

    @Override
    public List<HistoricActivityInstance> getActivityListByProcessInstanceId(String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc().list();
    }

    @Override
    public List<HistoricActivityInstance> getHistoricActivityListByExecutionId(String executionId) {
        return historyService.createHistoricActivityInstanceQuery().executionId(executionId).list();
    }

    /**
     * 判断指定用户，是否是当前任务的审批人
     *
     * @param userId 用户编号
     * @param task   任务
     * @return 是否
     */
    private boolean isAssignUserTask(Long userId, Task task) {
        Long assignee = NumberUtil.parseLong(task.getAssignee(), null);
        return ObjectUtil.equals(userId, assignee);
    }

    /**
     * 判断指定用户，是否是当前任务的拥有人
     *
     * @param userId 用户编号
     * @param task   任务
     * @return 是否
     */
    private boolean isOwnerUserTask(Long userId, Task task) {
        Long assignee = NumberUtil.parseLong(task.getOwner(), null);
        return ObjectUtil.equal(userId, assignee);
    }

    /**
     * 判断指定用户，是否是当前任务的加签人
     *
     * @param userId 用户 Id
     * @param task   任务
     * @return 是否
     */
    private boolean isAddSignUserTask(Long userId, Task task) {
        return (isAssignUserTask(userId, task) || isOwnerUserTask(userId, task))
                && BpmTaskSignTypeEnum.of(task.getScopeType()) != null;
    }

    // ========== Update 写入相关方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(Long userId, @Valid BpmTaskApproveReqVO reqVO) {
        // 1.1 校验任务存在
        Task task = validateTask(userId, reqVO.getId(),false);
        // 1.2 校验流程实例存在
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        // 1.3 校验签名
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        Boolean signEnable = parseSignEnable(bpmnModel, task.getTaskDefinitionKey());
        FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        UserTask userTask = (UserTask) flowElement;
        if (signEnable && StrUtil.isEmpty(reqVO.getSignPicUrl())) {
            throw exception(TASK_SIGNATURE_NOT_EXISTS);
        }
        // 1.4 校验审批意见
        Boolean reasonRequire = parseReasonRequire(bpmnModel, task.getTaskDefinitionKey());
        if (reasonRequire && StrUtil.isEmpty(reqVO.getReason())) {
            throw exception(TASK_REASON_REQUIRE);
        }

        // 情况一：被委派的任务，不调用 complete 去完成任务
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            approveDelegateTask(reqVO, task);
            return;
        }

        // 情况二：审批有【后】加签的任务
        if (BpmTaskSignTypeEnum.AFTER.getType().equals(task.getScopeType())) {
            approveAfterSignTask(task, reqVO);
            return;
        }

        // 情况三：审批普通的任务。大多数情况下，都是这样
        // 2.1 更新 task 状态、原因、签字
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.APPROVE.getStatus(), reqVO.getReason());
        if (signEnable) {
            taskService.setVariableLocal(task.getId(), BpmnVariableConstants.TASK_SIGN_PIC_URL, reqVO.getSignPicUrl());
        }
        // 2.2 添加评论
        taskService.addComment(task.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.APPROVE.getType(),
                BpmCommentTypeEnum.APPROVE.formatComment(reqVO.getReason()));
        // 2.3 调用 BPM complete 去完成任务
        // 其中，variables 是存储动态表单到 local 任务级别。过滤一下，避免 ProcessInstance 系统级的变量被占用
        Map<String, Object> variables = new HashMap<>();
        if (CollUtil.isNotEmpty(reqVO.getVariables())) {
            variables = FlowableUtils.filterTaskFormVariable(reqVO.getVariables());
            // 处理表单变量，确保数值类型变量以字符串形式存储
            variables = FlowableUtils.processFormVariables(variables);
            log.info("[approveTask][任务({}) 流程({}) 处理后的表单变量：{}]",
                    task.getId(), task.getProcessInstanceId(), variables);
            // 修改表单的值需要存储到 ProcessInstance 变量
            runtimeService.setVariables(task.getProcessInstanceId(), variables);
        }

        // 处理审批时的自选审批人
        if (CollUtil.isNotEmpty(reqVO.getStartUserSelectAssignees())) {
            log.info("[approveTask][任务({}) 流程({}) 处理审批时的自选审批人：{}]",
                    task.getId(), task.getProcessInstanceId(), reqVO.getStartUserSelectAssignees());

            // 获取当前流程实例中已有的自选审批人变量
            Object existingAssigneesObj = instance.getProcessVariables().get(
                    BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES);

            // 合并自选审批人
            Map<String, List<Long>> mergedAssignees = new HashMap<>();
            if (existingAssigneesObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, List<Long>> existingAssignees = (Map<String, List<Long>>) existingAssigneesObj;
                mergedAssignees.putAll(existingAssignees);
            }

            // 添加新的自选审批人，如果已存在则覆盖
            mergedAssignees.putAll(reqVO.getStartUserSelectAssignees());

            // 更新流程实例变量
            runtimeService.setVariable(task.getProcessInstanceId(),
                    BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                    mergedAssignees);

            // 如果variables是空的，需要初始化一个，以便传递给complete方法
            variables = new HashMap<>(variables);
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES, mergedAssignees);
        }

        // 2.4 完成任务
        if (!variables.isEmpty()) {
            taskService.complete(task.getId(), variables, true);
        } else {
            taskService.complete(task.getId());
        }
        // 清理退回标识，恢复节点的自动审批能力
        try {
            runtimeService.removeVariable(task.getProcessInstanceId(),
                    String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, task.getTaskDefinitionKey()));
        } catch (FlowableObjectNotFoundException e) {
            log.info("[approveTask][删除退回标识变量时出错: {}]", e.getMessage());
        }

        //判断当前节点userTask是否在并行网关或者包容网关内或者是多实例的 如果是

        // 判断当前任务是否为并行多实例任务
        boolean isParallelMultiInstance = userTask != null && userTask.getLoopCharacteristics() != null &&
                !((MultiInstanceLoopCharacteristics) userTask.getLoopCharacteristics()).isSequential();

        // 判断当前任务是否在并行网关内或是并行多实例任务
        boolean isMultiInstance = isParallelMultiInstance || (userTask != null && isInParallelGateway(userTask));

        if(isMultiInstance) {
            //发送通过的通知
            // 获取当前处理任务的用户
            AdminUserRespDTO currentUser = adminUserApi.getUser(Long.valueOf(task.getAssignee()));
            // 获取流程发起人信息（如果需要的话）
            log.info(
                    "发送通知 -->  当前处理用户 :{} ",currentUser
            );
            messageService.sendMessageCountersign(BpmTaskConvert.INSTANCE.convert(instance, currentUser, task),true);
        };
        // 【加签专属】处理加签任务
        handleParentTaskIfSign(task.getParentTaskId());
    }

    /**
     * 审批通过存在"后加签"的任务。
     * <p>
     * 注意：该任务不能马上完成，需要一个中间状态（APPROVING），并激活剩余所有子任务（PROCESS）为可审批处理
     * 如果马上完成，则会触发下一个任务，甚至如果没有下一个任务则流程实例就直接结束了！
     *
     * @param task  当前任务
     * @param reqVO 前端请求参数
     */
    private void approveAfterSignTask(Task task, BpmTaskApproveReqVO reqVO) {
        // 更新父 task 状态 + 原因
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.APPROVING.getStatus(), reqVO.getReason());

        // 2. 激活子任务
        List<Task> childrenTaskList = getTaskListByParentTaskId(task.getId());
        for (Task childrenTask : childrenTaskList) {
            taskService.resolveTask(childrenTask.getId());
            // 更新子 task 状态
            updateTaskStatus(childrenTask.getId(), BpmTaskStatusEnum.RUNNING.getStatus());
        }
    }

    /**
     * 如果父任务是有前后【加签】的任务，如果它【加签】出来的子任务都被处理，需要处理父任务：
     * <p>
     * 1. 如果是【向前】加签，则需要重新激活父任务，让它可以被审批
     * 2. 如果是【向后】加签，则需要完成父任务，让它完成审批
     *
     * @param parentTaskId 父任务编号
     */
    private void handleParentTaskIfSign(String parentTaskId) {
        if (StrUtil.isBlank(parentTaskId)) {
            return;
        }
        // 1.1 判断是否还有子任务。如果没有，就不处理
        Long childrenTaskCount = getTaskCountByParentTaskId(parentTaskId);
        if (childrenTaskCount > 0) {
            return;
        }
        // 1.2 只处理加签的父任务
        Task parentTask = validateTaskExist(parentTaskId);
        String scopeType = parentTask.getScopeType();
        if (BpmTaskSignTypeEnum.of(scopeType) == null) {
            return;
        }

        // 2. 子任务已处理完成，清空 scopeType 字段，修改 parentTask 信息，方便后续可以继续向前后向后加签
        TaskEntityImpl parentTaskImpl = (TaskEntityImpl) parentTask;
        parentTaskImpl.setScopeType(null);
        taskService.saveTask(parentTaskImpl);

        // 3.1 情况一：处理向【向前】加签
        if (BpmTaskSignTypeEnum.BEFORE.getType().equals(scopeType)) {
            // 3.1.1 owner 重新赋值给父任务的 assignee，这样它就可以被审批
            taskService.resolveTask(parentTaskId);
            // 3.1.2 更新流程任务 status
            updateTaskStatus(parentTaskId, BpmTaskStatusEnum.RUNNING.getStatus());
            // 3.2 情况二：处理向【向后】加签
        } else if (BpmTaskSignTypeEnum.AFTER.getType().equals(scopeType)) {
            // 只有 parentTask 处于 APPROVING 的情况下，才可以继续 complete 完成
            // 否则，一个未审批的 parentTask 任务，在加签出来的任务都被减签的情况下，就直接完成审批，这样会存在问题
            Integer status = (Integer) parentTask.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
            if (ObjectUtil.notEqual(status, BpmTaskStatusEnum.APPROVING.getStatus())) {
                return;
            }
            // 3.2.2 完成自己（因为它已经没有子任务，所以也可以完成）
            updateTaskStatus(parentTaskId, BpmTaskStatusEnum.APPROVE.getStatus());
            taskService.complete(parentTaskId);
        }

        // 4. 递归处理父任务
        handleParentTaskIfSign(parentTask.getParentTaskId());
    }

    /**
     * 审批被委派的任务
     *
     * @param reqVO 前端请求参数，包含当前任务ID，审批意见等
     * @param task  当前被审批的任务
     */
    private void approveDelegateTask(BpmTaskApproveReqVO reqVO, Task task) {
        // 1. 添加审批意见
        AdminUserRespDTO currentUser = adminUserApi.getUser(WebFrameworkUtils.getLoginUserId());
        AdminUserRespDTO ownerUser = adminUserApi.getUser(NumberUtils.parseLong(task.getOwner())); // 发起委托的用户
        Assert.notNull(ownerUser, "委派任务找不到原审批人，需要检查数据");
        taskService.addComment(reqVO.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.DELEGATE_END.getType(),
                BpmCommentTypeEnum.DELEGATE_END.formatComment(currentUser.getNickname(), ownerUser.getNickname(), reqVO.getReason()));

        // 2.1 调用 resolveTask 完成任务。
        // 底层调用 TaskHelper.changeTaskAssignee(task, task.getOwner())：将 owner 设置为 assignee
        taskService.resolveTask(task.getId());
        // 2.2 更新 task 状态 + 原因
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RUNNING.getStatus(), reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(Long userId, @Valid BpmTaskRejectReqVO reqVO) {
        // 1.1 校验任务存在
        Task task = validateTask(userId, reqVO.getId(),false);
        // 1.2 校验流程实例存在
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 2.1 更新流程任务为不通过
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.REJECT.getStatus(), reqVO.getReason());
        // 2.2 添加流程评论
        taskService.addComment(task.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.getType(),
                BpmCommentTypeEnum.REJECT.formatComment(reqVO.getReason()));
        // 2.3 如果当前任务时被加签的，则加它的根任务也标记成未通过
        // 疑问：为什么要标记未通过呢？
        // 回答：例如说 A 任务被向前加签除 B 任务时，B 任务被审批不通过，此时 A 会被取消。而 yudao-ui-admin-vue3 不展示"已取消"的任务，导致展示不出审批不通过的细节。
        if (task.getParentTaskId() != null) {
            String rootParentId = getTaskRootParentId(task);
            updateTaskStatusAndReason(rootParentId, BpmTaskStatusEnum.REJECT.getStatus(),
                    BpmCommentTypeEnum.REJECT.formatComment("加签任务不通过"));
            taskService.addComment(rootParentId, task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.getType(),
                    BpmCommentTypeEnum.REJECT.formatComment("加签任务不通过"));
        }

        // 3. 根据不同的 RejectHandler 处理策略
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        // 3.1 情况一：驳回到指定的任务节点
        BpmUserTaskRejectHandlerTypeEnum userTaskRejectHandlerType = BpmnModelUtils.parseRejectHandlerType(userTaskElement);
        if (userTaskRejectHandlerType == BpmUserTaskRejectHandlerTypeEnum.RETURN_USER_TASK) {
            String returnTaskId = BpmnModelUtils.parseReturnTaskId(userTaskElement);
            Assert.notNull(returnTaskId, "退回的节点不能为空");
            returnTask(userId, new BpmTaskReturnReqVO().setId(task.getId())
                    .setTargetTaskDefinitionKey(returnTaskId).setReason(reqVO.getReason()));
            return;
        }
        // 3.2 情况二：直接结束，审批不通过
        processInstanceService.updateProcessInstanceReject(instance, reqVO.getReason()); // 标记不通过
        moveTaskToEnd(task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.formatComment(reqVO.getReason())); // 结束流程
    }


    /**
     * 更新流程任务的 status 状态
     *
     * @param id     任务编号
     * @param status 状态
     */
    private void updateTaskStatus(String id, Integer status) {
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_STATUS, status);
    }

    /**
     * 更新流程任务的 status 状态、reason 理由
     *
     * @param id     任务编号
     * @param status 状态
     * @param reason 理由（审批通过、审批不通过的理由）
     */
    private void updateTaskStatusAndReason(String id, Integer status, String reason) {
        updateTaskStatus(id, status);
        String oldReason = (String) taskService.getVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_REASON);
        String newReason = StrUtil.isNotBlank(oldReason)
                ? oldReason + System.lineSeparator() + reason
                : reason;
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_REASON, newReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long userId, BpmTaskReturnReqVO reqVO) {
        // 1. 校验任务存在
        Task task = validateTask(userId, reqVO.getId(),reqVO.getManagerial());
        // 2. 校验流程实例存在
        System.out.println((reqVO.getProcessInstanceId() == null || reqVO.getProcessInstanceId().isEmpty())
                ? task.getProcessInstanceId()
                : reqVO.getProcessInstanceId());
        ProcessInstance instance = processInstanceService.getProcessInstance((reqVO.getProcessInstanceId() == null || reqVO.getProcessInstanceId().isEmpty())
                ? task.getProcessInstanceId()
                : reqVO.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 3. 更新任务状态并添加评论
        taskService.addComment(task.getId(), instance.getId(),
                BpmCommentTypeEnum.RETURN.getType(), reqVO.getReason());
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RETURN.getStatus(), reqVO.getReason());
        // 如果退回的节点由发起人审批，则标记避免自动通过
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(instance.getProcessDefinitionId());
        FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, reqVO.getTargetTaskDefinitionKey());
        Integer candidateStrategy = BpmnModelUtils.parseCandidateStrategy(targetElement);
        if (ObjectUtil.equal(candidateStrategy, BpmTaskCandidateStrategyEnum.START_USER.getStrategy())) {
            System.out.println("来到这里");
            runtimeService.setVariable(instance.getId(),
                    String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, reqVO.getTargetTaskDefinitionKey()),
                    Boolean.TRUE);
        }

        log.info("[returnTask][任务({}) 跳转到节点({})]", task.getId(), reqVO.getTargetTaskDefinitionKey());
        managementService.executeCommand(new BackTaskCmd(runtimeService, task.getId(), reqVO.getTargetTaskDefinitionKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String processInstanceId, String targetTaskDefinitionKey) {
        log.info("[returnTask][processInstanceId({}) 跳转到节点({})]", processInstanceId, targetTaskDefinitionKey);

        List<Task> taskList = getRunningTaskListByProcessInstanceId(processInstanceId, null, null);
        if (CollUtil.isEmpty(taskList)) {
            throw new FlowableObjectNotFoundException("processInstanceId " + processInstanceId + " doesn't have running task", Task.class);
        }

        Task task = taskList.getFirst();
        managementService.executeCommand(new BackTaskCmd(runtimeService, task.getId(), targetTaskDefinitionKey));
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(Long userId, BpmTaskDelegateReqVO reqVO) {
        String taskId = reqVO.getId();
        // 1.1 校验任务
        Task task = validateTask(userId, reqVO.getId(),false);
        if (task.getAssignee().equals(reqVO.getDelegateUserId().toString())) { // 校验当前审批人和被委派人不是同一人
            throw exception(TASK_DELEGATE_FAIL_USER_REPEAT);
        }
        // 1.2 校验目标用户存在
        AdminUserRespDTO delegateUser = adminUserApi.getUser(reqVO.getDelegateUserId());
        if (delegateUser == null) {
            throw exception(TASK_DELEGATE_FAIL_USER_NOT_EXISTS);
        }

        // 2. 添加委托意见
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId);
        String delegateComment = BpmCommentTypeEnum.DELEGATE_START.formatComment(currentUser.getNickname(),
                delegateUser.getNickname(), reqVO.getReason());
        taskService.addComment(taskId, task.getProcessInstanceId(),
                delegateComment, reqVO.getReason());
        // 新增流程评论记录，同时设置委派原因，便于委派后立即在待办中展示
        addCommentAndReason(userId, task,
                BpmCommentTypeEnum.DELEGATE_START.formatComment(currentUser.getNickname(),
                        delegateUser.getNickname(), reqVO.getReason()), delegateComment);
        taskService.setVariableLocal(task.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_IS_DELEGATE,1);
        //给流程增加参数
        // 3.1 设置任务所有人 (owner) 为原任务的处理人 (assignee)
        taskService.setOwner(taskId, task.getAssignee());
        // 3.2 执行委派，将任务委派给 delegateUser
        taskService.delegateTask(taskId, reqVO.getDelegateUserId().toString());
        // 补充说明：委托不单独设置状态。如果需要，可通过 Task 的 DelegationState 字段，判断是否为 DelegationState.PENDING 委托中
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(Long userId, BpmTaskTransferReqVO reqVO) {
        String taskId = reqVO.getId();
        // 1.1 校验任务
        Task task = validateTask(userId, reqVO.getId(),reqVO.getManagerial());
        if (task.getAssignee().equals(reqVO.getAssigneeUserId().toString())) { // 校验当前审批人和被转派人不是同一人
            throw exception(TASK_TRANSFER_FAIL_USER_REPEAT);
        }
        // 1.2 校验目标用户存在
        AdminUserRespDTO assigneeUser = adminUserApi.getUser(reqVO.getAssigneeUserId());
        if (assigneeUser == null) {
            throw exception(TASK_TRANSFER_FAIL_USER_NOT_EXISTS);
        }

        // 2. 添加委托意见
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId);
        String transferComment = BpmCommentTypeEnum.TRANSFER.formatComment(currentUser.getNickname(),
                assigneeUser.getNickname(), reqVO.getReason());
        taskService.addComment(taskId, task.getProcessInstanceId(), transferComment, reqVO.getReason());
        // 新增流程评论记录，同时设置转办原因，便于转办后立即在待办中展示
        addCommentAndReason(userId, task, reqVO.getReason(), transferComment);
        taskService.setVariableLocal(task.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_IS_DELEGATE,2);
        // 3.1 设置任务所有人 (owner) 为原任务的处理人 (assignee)
        taskService.setOwner(taskId, task.getAssignee());
        // 3.2 执行转派（审批人），将任务转派给 assigneeUser
        // 委托（ delegate）和转派（transfer）的差别，就在这块的调用！！！！
        taskService.setAssignee(taskId, reqVO.getAssigneeUserId().toString());
    }

    /**
     * 记录流程评论，并保存任务原因，方便在待办中直接展示
     *
     * @param userId  用户编号
     * @param task    当前任务
     * @param comment 评论内容
     * @param reason  原因
     */
    private void addCommentAndReason(Long userId, Task task, String comment, String reason) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(task.getProcessInstanceId());
        if (instance != null) {
            BpmProcessInstanceCommentCreateReqVO commentVO = new BpmProcessInstanceCommentCreateReqVO();
            commentVO.setProcessInstanceId(instance.getId());
            commentVO.setProcessName(instance.getName());
            commentVO.setContent(comment);
            commentService.createComment(userId, commentVO);
        }
        // 先读取原本的原因信息，避免覆盖
        String oldReason = (String) taskService.getVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_REASON);
        String newReason = StrUtil.isNotBlank(oldReason)
                ? oldReason + System.lineSeparator() + reason
                : reason;
        taskService.setVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_REASON, newReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveTaskToEnd(String processInstanceId, String reason) {
        List<Task> taskList = getRunningTaskListByProcessInstanceId(processInstanceId, null, null);
        if (CollUtil.isEmpty(taskList)) {
            return;
        }

        // 1. 其它未结束的任务，直接取消
        // 疑问：为什么不通过 updateTaskStatusWhenCanceled 监听取消，而是直接提前调用呢？
        // 回答：详细见 updateTaskStatusWhenCanceled 的方法，加签的场景
        taskList.forEach(task -> {
            Integer otherTaskStatus = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
            if (BpmTaskStatusEnum.isEndStatus(otherTaskStatus)) {
                return;
            }
            processTaskCanceled(task.getId());
        });

        // 2. 终止流程
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(taskList.get(0).getProcessDefinitionId());
        List<String> activityIds = CollUtil.newArrayList(convertSet(taskList, Task::getTaskDefinitionKey));
        EndEvent endEvent = BpmnModelUtils.getEndEvent(bpmnModel);
        Assert.notNull(endEvent, "结束节点不能未空");
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdsToSingleActivityId(activityIds, endEvent.getId())
                .changeState();

        // 3. 特殊：如果跳转到 EndEvent 流程还未结束， 执行 deleteProcessInstance 方法
        // TODO 芋艿：目前发现并行分支情况下，会存在这个情况，后续看看有没更好的方案；
        List<Execution> executions = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).list();
        if (CollUtil.isNotEmpty(executions)) {
            log.warn("[moveTaskToEnd][执行跳转到 EndEvent 后, 流程实例未结束，强制执行 deleteProcessInstance 方法]");
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSignTask(Long userId, BpmTaskSignCreateReqVO reqVO) {
        // 1. 获取和校验任务
        TaskEntityImpl taskEntity = validateTaskCanCreateSign(userId, reqVO);
        List<AdminUserRespDTO> userList = adminUserApi.getUserList(reqVO.getUserIds());
        if (CollUtil.isEmpty(userList)) {
            throw exception(TASK_SIGN_CREATE_USER_NOT_EXIST);
        }

        // 2. 处理当前任务
        // 2.1 开启计数功能，主要用于为了让表 ACT_RU_TASK 中的 SUB_TASK_COUNT_ 字段记录下总共有多少子任务，后续可能有用
        taskEntity.setCountEnabled(true);
        // 2.2 向前加签，设置 owner，置空 assign。等子任务都完成后，再调用 resolveTask 重新将 owner 设置为 assign
        // 原因是：不能和向前加签的子任务一起审批，需要等前面的子任务都完成才能审批
        if (reqVO.getType().equals(BpmTaskSignTypeEnum.BEFORE.getType())) {
            taskEntity.setOwner(taskEntity.getAssignee());
            taskEntity.setAssignee(null);
        }
        // 2.4 记录加签方式，完成任务时需要用到判断
        taskEntity.setScopeType(reqVO.getType());
        // 2.5 保存当前任务修改后的值
        taskService.saveTask(taskEntity);
        // 2.6 更新 task 状态为 WAIT，只有在向前加签的时候
        if (reqVO.getType().equals(BpmTaskSignTypeEnum.BEFORE.getType())) {
            updateTaskStatus(taskEntity.getId(), BpmTaskStatusEnum.WAIT.getStatus());
        }

        // 3. 创建加签任务
        createSignTaskList(convertList(reqVO.getUserIds(), String::valueOf), taskEntity);

        // 4. 记录加签的评论到 task 任务
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId);
        String comment = StrUtil.format(BpmCommentTypeEnum.ADD_SIGN.getComment(),
                currentUser.getNickname(), BpmTaskSignTypeEnum.nameOfType(reqVO.getType()),
                String.join(",", convertList(userList, AdminUserRespDTO::getNickname)), reqVO.getReason());
        taskService.addComment(reqVO.getId(), taskEntity.getProcessInstanceId(), BpmCommentTypeEnum.ADD_SIGN.getType(), comment);
    }

    /**
     * 校验任务是否可以加签，主要校验加签类型是否一致：
     * <p>
     * 1. 如果存在"向前加签"的任务，则不能"向后加签"
     * 2. 如果存在"向后加签"的任务，则不能"向前加签"
     *
     * @param userId 当前用户 ID
     * @param reqVO  请求参数，包含任务 ID 和加签类型
     * @return 当前任务
     */
    private TaskEntityImpl validateTaskCanCreateSign(Long userId, BpmTaskSignCreateReqVO reqVO) {
        TaskEntityImpl taskEntity = (TaskEntityImpl) validateTask(userId, reqVO.getId(),false);
        // 向前加签和向后加签不能同时存在
        if (taskEntity.getScopeType() != null
                && ObjectUtil.notEqual(taskEntity.getScopeType(), reqVO.getType())) {
            throw exception(TASK_SIGN_CREATE_TYPE_ERROR,
                    BpmTaskSignTypeEnum.nameOfType(taskEntity.getScopeType()), BpmTaskSignTypeEnum.nameOfType(reqVO.getType()));
        }

        // 同一个 key 的任务，审批人不重复
        List<Task> taskList = taskService.createTaskQuery().processInstanceId(taskEntity.getProcessInstanceId())
                .taskDefinitionKey(taskEntity.getTaskDefinitionKey()).list();
        List<Long> currentAssigneeList = convertListByFlatMap(taskList, task -> // 需要考虑 owner 的情况，因为向后加签时，它暂时没 assignee 而是 owner
                Stream.of(NumberUtils.parseLong(task.getAssignee()), NumberUtils.parseLong(task.getOwner())));
        if (CollUtil.containsAny(currentAssigneeList, reqVO.getUserIds())) {
            List<AdminUserRespDTO> userList = adminUserApi.getUserList(CollUtil.intersection(currentAssigneeList, reqVO.getUserIds()));
            throw exception(TASK_SIGN_CREATE_USER_REPEAT, String.join(",", convertList(userList, AdminUserRespDTO::getNickname)));
        }
        return taskEntity;
    }

    /**
     * 创建加签子任务
     *
     * @param userIds    被加签的用户 ID
     * @param taskEntity 被加签的任务
     */
    private void createSignTaskList(List<String> userIds, TaskEntityImpl taskEntity) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        // 创建加签人的新任务，全部基于 taskEntity 为父任务来创建
        for (String addSignId : userIds) {
            if (StrUtil.isBlank(addSignId)) {
                continue;
            }
            createSignTask(taskEntity, addSignId);
        }
    }

    /**
     * 创建加签子任务
     *
     * @param parentTask 父任务
     * @param assignee   子任务的执行人
     */
    private void createSignTask(TaskEntityImpl parentTask, String assignee) {
        // 1. 生成子任务
        TaskEntityImpl task = (TaskEntityImpl) taskService.newTask(IdUtil.fastSimpleUUID());
        BpmTaskConvert.INSTANCE.copyTo(parentTask, task);

        // 2.1 向前加签，设置审批人
        if (BpmTaskSignTypeEnum.BEFORE.getType().equals(parentTask.getScopeType())) {
            task.setAssignee(assignee);
            // 2.2 向后加签，设置 owner 不设置 assignee 是因为不能同时审批，需要等父任务完成
        } else {
            task.setOwner(assignee);
        }
        // 2.3 保存子任务
        taskService.saveTask(task);

        // 3. 向后前签，设置子任务的状态为 WAIT，因为需要等父任务审批完
        if (BpmTaskSignTypeEnum.AFTER.getType().equals(parentTask.getScopeType())) {
            updateTaskStatus(task.getId(), BpmTaskStatusEnum.WAIT.getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSignTask(Long userId, BpmTaskSignDeleteReqVO reqVO) {
        // 1.1 校验 task 可以被减签
        Task task = validateTaskCanSignDelete(reqVO.getId());
        // 1.2 校验取消人存在
        AdminUserRespDTO cancelUser = null;
        if (StrUtil.isNotBlank(task.getAssignee())) {
            cancelUser = adminUserApi.getUser(NumberUtils.parseLong(task.getAssignee()));
        }
        if (cancelUser == null && StrUtil.isNotBlank(task.getOwner())) {
            cancelUser = adminUserApi.getUser(NumberUtils.parseLong(task.getOwner()));
        }
        Assert.notNull(cancelUser, "任务中没有所有者和审批人，数据错误");

        // 2.1 获得子任务列表，包括子任务的子任务
        List<Task> childTaskList = getAllChildTaskList(task);
        childTaskList.add(task);
        // 2.2 更新子任务为已取消
        String cancelReason = StrUtil.format("任务被取消，原因：由于[{}]操作[减签]，", cancelUser.getNickname());
        childTaskList.forEach(childTask -> updateTaskStatusAndReason(childTask.getId(), BpmTaskStatusEnum.CANCEL.getStatus(), cancelReason));
        // 2.3 删除任务和所有子任务
        taskService.deleteTasks(convertList(childTaskList, Task::getId));

        // 3. 记录日志到父任务中。先记录日志是因为，通过 handleParentTask 方法之后，任务可能被完成了，并且不存在了，会报异常，所以先记录
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        taskService.addComment(task.getParentTaskId(), task.getProcessInstanceId(), BpmCommentTypeEnum.SUB_SIGN.getType(),
                StrUtil.format(BpmCommentTypeEnum.SUB_SIGN.getComment(), user.getNickname(), cancelUser.getNickname()));

        // 4. 处理当前任务的父任务
        handleParentTaskIfSign(task.getParentTaskId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long userId, BpmTaskCancelReqVO reqVO) {
        // 1. 校验任务存在
        Task task = validateTask(userId, reqVO.getId(), reqVO.getManagerial());

        // 2. 获取所有子任务，包括子任务的子任务
        List<Task> childTaskList = getAllChildTaskList(task);
        childTaskList.add(task);

        // 2.1 更新子任务状态和原因
        String cancelReason = StrUtil.format("任务被取消，原因：{}", reqVO.getReason());
        childTaskList.forEach(t -> updateTaskStatusAndReason(t.getId(), BpmTaskStatusEnum.CANCEL.getStatus(), cancelReason));

        // 2.2 添加取消意见
        taskService.addComment(task.getId(), task.getProcessInstanceId(),
                BpmCommentTypeEnum.CANCEL.getType(), BpmCommentTypeEnum.CANCEL.formatComment(reqVO.getReason()));

        // 2.3 删除任务以及所有子任务
        childTaskList.forEach(t -> managementService.executeCommand(
                new DeleteTaskCmd(t.getExecutionId(), cancelReason)));

        // 3. 处理父任务
        handleParentTaskIfSign(task.getParentTaskId());
    }

    @Override
    public void copyTask(Long userId, BpmTaskCopyReqVO reqVO) {
        Set<Long> userIds = new HashSet<>(reqVO.getCopyUserIds());
        if (Boolean.TRUE.equals(reqVO.getCopySelf())) {
            userIds.add(userId);
        }
        processInstanceCopyService.createProcessInstanceCopy(userIds, reqVO.getReason(), reqVO.getId());
    }

    /**
     * 校验任务是否能被减签
     *
     * @param id 任务编号
     * @return 任务信息
     */
    private Task validateTaskCanSignDelete(String id) {
        Task task = validateTaskExist(id);
        if (task.getParentTaskId() == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        Task parentTask = getTask(task.getParentTaskId());
        if (parentTask == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        if (BpmTaskSignTypeEnum.of(parentTask.getScopeType()) == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        return task;
    }

    // ========== Event 事件相关方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTaskCreated(Task task) {
        // 1. 设置为待办中
        Integer status = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
        if (status != null) {
            log.error("[updateTaskStatusWhenCreated][taskId({}) 已经有状态({})]", task.getId(), status);
            return;
        }
        updateTaskStatus(task.getId(), BpmTaskStatusEnum.RUNNING.getStatus());

        // 1.5 处理工作时间计算 - 重新计算任务的dueDate
        ProcessInstance processInstance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (processInstance != null) {
            BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
            FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());

            // 检查是否启用工作时间计算
            BpmnModelUtils.WorkTimeConfig workTimeConfig = BpmnModelUtils.getWorkTimeConfig(userTaskElement);

            if (workTimeConfig.isEnabled()) {
                try {
                    LocalDateTime createTime = DateUtils.of(task.getCreateTime());
                    Duration originalDuration = null;
                    String workDurationStr = BpmnModelUtils.parseWorkTimeDuration(userTaskElement);
                    if (StrUtil.isNotEmpty(workDurationStr)) {
                        originalDuration = Duration.parse(workDurationStr);
                    } else if (task.getDueDate() != null) {
                        LocalDateTime originalDueTime = DateUtils.of(task.getDueDate());
                        originalDuration = Duration.between(createTime, originalDueTime);
                    }

                    if (originalDuration == null) {
                        log.warn("[processTaskCreated][taskId({}) 无法获取工时时长，跳过计算]", task.getId());
                        return;
                    }

                    log.info("[processTaskCreated][taskId({}) 启用工作时间计算: 创建时间={}, 时长={}秒]",
                            task.getId(), createTime.format(LOG_DATE_TIME_FORMATTER), originalDuration.toSeconds());
                    LocalDateTime workTimeDueTime = workTimeService.calculateDueTime(createTime, originalDuration, workTimeConfig.getType());

                    if (workTimeDueTime != null) {
                        // 保存工作时间到期时间(时间戳)到任务变量
                        long dueTs = workTimeDueTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        taskService.setVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_WORK_DUE_DATE, dueTs);
                        log.info("[processTaskCreated][taskId({}) 工作时间到期时间计算完成: {}({})]",
                                task.getId(), workTimeDueTime.format(LOG_DATE_TIME_FORMATTER), dueTs);
                        // 验证工作时间配置
                        // 计算并保存通知时间(时间戳)，默认在工作时长的80%
                        long notifyMillis = Math.round(originalDuration.toMillis() * 0.8);
                        Duration notifyDuration = Duration.ofMillis(notifyMillis);
                        LocalDateTime notifyTime = workTimeService.calculateDueTime(createTime, notifyDuration, workTimeConfig.getType());
                        if (notifyTime != null) {
                            long notifyTs = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            taskService.setVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_WORK_NOTIFY_DATE, notifyTs);
                            log.info("[processTaskCreated][taskId({}) 工作时间通知时间计算完成: {}({})]",
                                    task.getId(), notifyTime.format(LOG_DATE_TIME_FORMATTER), notifyTs);
                        } else {
                            log.warn("[processTaskCreated][taskId({}) 通知时间计算失败]", task.getId());
                        }
                        String configInfo = BpmnModelUtils.validateAndLogWorkTimeConfig(userTaskElement, task.getId());
                        log.info("[processTaskCreated][{}]", configInfo);
                    } else {
                        log.warn("[processTaskCreated][taskId({}) 工作时间计算失败]", task.getId());
                    }
                } catch (Exception e) {
                    log.error("[processTaskCreated][taskId({}) 工作时间计算异常]", task.getId(), e);
                }
            } else {
                log.debug("[processTaskCreated][taskId({}) 未启用工作时间计算]", task.getId());
            }
        }

        // 2. 处理自动通过的情况，例如说：1）无审批人时，是否自动通过、不通过；2）非【人工审核】时，是否自动通过、不通过
        if (processInstance == null) {
            log.error("[processTaskCreated][taskId({}) 没有找到流程实例]", task.getId());
            return;
        }
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        Integer approveType = BpmnModelUtils.parseApproveType(userTaskElement);
        Integer assignEmptyHandlerType = BpmnModelUtils.parseAssignEmptyHandlerType(userTaskElement);
        registerAfterTransaction(task, t -> {
            // 特殊情况一：【人工审核】审批人为空，根据配置是否要自动通过、自动拒绝
            if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.USER.getType())) {
                // 如果有审批人、或者拥有人，则说明不满足情况一，不自动通过、不自动拒绝
                if (!ObjectUtil.isAllEmpty(t.getAssignee(), t.getOwner())) {
                    return;
                }
                if (ObjectUtil.equal(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.APPROVE.getType())) {
                    taskExecutor.execute(() -> getSelf().approveTask(null, new BpmTaskApproveReqVO()
                            .setId(t.getId()).setReason(BpmReasonEnum.ASSIGN_EMPTY_APPROVE.getReason())));
                } else if (ObjectUtil.equal(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.REJECT.getType())) {
                    taskExecutor.execute(() -> getSelf().rejectTask(null, new BpmTaskRejectReqVO()
                            .setId(t.getId()).setReason(BpmReasonEnum.ASSIGN_EMPTY_REJECT.getReason())));
                }
                // 特殊情况二：【自动审核】审批类型为自动通过、不通过
            } else {
                if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType())) {
                    taskExecutor.execute(() -> getSelf().approveTask(null, new BpmTaskApproveReqVO()
                            .setId(t.getId()).setReason(BpmReasonEnum.APPROVE_TYPE_AUTO_APPROVE.getReason())));
                } else if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
                    taskExecutor.execute(() -> getSelf().rejectTask(null, new BpmTaskRejectReqVO()
                            .setId(t.getId()).setReason(BpmReasonEnum.APPROVE_TYPE_AUTO_REJECT.getReason())));
                }
            }
        });
    }

    /**
     * 重要补充说明：该方法目前主要有两个情况会调用到：
     * <p>
     * 1. 或签场景 + 审批通过：一个或签有多个审批时，如果 A 审批通过，其它或签 B、C 等任务会被 Flowable 自动删除，此时需要通过该方法更新状态为已取消
     * 2. 审批不通过：在 {@link #rejectTask(Long, BpmTaskRejectReqVO)} 不通过时，对于加签的任务，不会被 Flowable 删除，此时需要通过该方法更新状态为已取消
     */
    @Override
    public void processTaskCanceled(String taskId) {
        try {
            Task task = getTask(taskId);
            // 1. 可能只是活动，不是任务，所以查询不到
            if (task == null) {
                log.debug("[processTaskCanceled][taskId({}) 任务不存在，忽略取消操作]", taskId);
                return;
            }

            // 2. 更新 task 状态 + 原因
            Integer status = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
            if (BpmTaskStatusEnum.isEndStatus(status)) {
                log.debug("[processTaskCanceled][taskId({}) 处于结果({})，无需进行更新]", taskId, status);
                return;
            }

            try {
                log.info("自动取消了————>之前状态{}",status);
                updateTaskStatusAndReason(taskId, BpmTaskStatusEnum.CANCEL.getStatus(),
                        BpmReasonEnum.CANCEL_BY_SYSTEM.getReason());
            } catch (FlowableException ex) {
                // 任务可能在并发情况下被删除，忽略异常
                log.debug("[processTaskCanceled][taskId({}) 任务已被删除，忽略状态更新]", taskId);
            }
        } catch (Exception e) {
            log.warn("[processTaskCanceled][taskId({}) 处理任务取消异常]", taskId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTaskAssigned(Task task) {
        // 发送通知。在事务提交时，批量执行操作，所以直接查询会无法查询到 ProcessInstance，所以这里是通过监听事务的提交来实现。
        registerAfterTransaction(task, t -> {
            if (StrUtil.isEmpty(t.getAssignee())) {
                log.error("[processTaskAssigned][taskId({}) 没有分配到负责人]", t.getId());
                return;
            }
            ProcessInstance processInstance = processInstanceService.getProcessInstance(t.getProcessInstanceId());
            if (processInstance == null) {
                log.error("[processTaskAssigned][taskId({}) 没有找到流程实例]", t.getId());
                return;
            }

            ProcessDefinition definition = bpmProcessDefinitionService.getProcessDefinition(t.getProcessDefinitionId());
            BpmProcessDefinitionInfoDO processDefinitionInfo = bpmProcessDefinitionService.getProcessDefinitionInfo(t.getProcessDefinitionId());
            String modelId = processDefinitionInfo != null ? processDefinitionInfo.getModelId() : null;
            Integer version = definition != null ? definition.getVersion() : null;
                    System.out.println("modelId--->"+modelId);
                    System.out.println("version--->"+version);

            // 根据转办配置自动调整审批人
            BpmTaskTransferConfigDO transferConfig = taskTransferConfigService.getActiveTaskTransferConfig(
                    Long.valueOf(t.getAssignee()), modelId, version);
                    System.out.println(transferConfig);
                    System.out.println();
                    System.out.println("t.getAssignee()--->"+t.getAssignee());
                    if (transferConfig != null) {
                        System.out.println("getFromUserId()--->"+ transferConfig.getFromUserId());
                        System.out.println();
                        System.out.println("------()--->"+ ObjectUtil.notEqual(transferConfig.getToUserId(), transferConfig.getFromUserId()) );
                        System.out.println("------()--->"+Long.valueOf(t.getAssignee()).equals(transferConfig.getFromUserId()));
                        if(ObjectUtil.notEqual(transferConfig.getToUserId(), transferConfig.getFromUserId()) && Long.valueOf(t.getAssignee()).equals(transferConfig.getFromUserId())){
                            System.out.println("我来到这里");
                            getSelf().transferTask(transferConfig.getFromUserId(), new BpmTaskTransferReqVO()
                                    .setId(t.getId())
                                    .setAssigneeUserId(transferConfig.getToUserId())
                                    .setReason(transferConfig.getReason())
                                    .setManagerial(true));
                            return;
                        }
                    }
            // 自动去重，通过自动审批的方式 TODO @芋艿 驳回的情况得考虑一下；@lesan：驳回后，又自动审批么？
            if (processDefinitionInfo == null) {
                log.error("[processTaskAssigned][taskId({}) 没有找到流程定义({})]", t.getId(), t.getProcessDefinitionId());
                return;
            }
            if (processDefinitionInfo.getAutoApprovalType() != null) {
                HistoricTaskInstanceQuery sameAssigneeQuery = historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(t.getProcessInstanceId())
                        .taskAssignee(t.getAssignee()) // 相同审批人
                        .taskVariableValueEquals(BpmnVariableConstants.TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus())
                        .finished();
                if (BpmAutoApproveTypeEnum.APPROVE_ALL.getType().equals(processDefinitionInfo.getAutoApprovalType())
                        && sameAssigneeQuery.count() > 0) {
                    taskExecutor.execute(() -> getSelf().approveTask(Long.valueOf(t.getAssignee()), new BpmTaskApproveReqVO().setId(t.getId())
                            .setReason(BpmAutoApproveTypeEnum.APPROVE_ALL.getName())));
                    return;
                }
                if (BpmAutoApproveTypeEnum.APPROVE_SEQUENT.getType().equals(processDefinitionInfo.getAutoApprovalType())) {
                    BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
                    if (bpmnModel == null) {
                        log.error("[processTaskAssigned][taskId({}) 没有找到流程模型({})]", t.getId(), t.getProcessDefinitionId());
                        return;
                    }
                    List<String> sourceTaskIds = convertList(BpmnModelUtils.getElementIncomingFlows( // 获取所有上一个节点
                                    BpmnModelUtils.getFlowElementById(bpmnModel, t.getTaskDefinitionKey())),
                            SequenceFlow::getSourceRef);
                    if (sameAssigneeQuery.taskDefinitionKeys(sourceTaskIds).count() > 0) {
                        taskExecutor.execute(() -> getSelf().approveTask(Long.valueOf(t.getAssignee()), new BpmTaskApproveReqVO().setId(t.getId())
                                .setReason(BpmAutoApproveTypeEnum.APPROVE_SEQUENT.getName())));
                        return;
                    }
                }
            }

            // 审批人与提交人为同一人时，根据 BpmUserTaskAssignStartUserHandlerTypeEnum 策略进行处理
            if (StrUtil.equals(t.getAssignee(), String.valueOf(FlowableUtils.getProcessInstanceStartUserId(processInstance)))) {
                // 判断是否为退回或者驳回：如果是退回或者驳回不走这个策略
                // TODO 芋艿：【优化】未来有没更好的判断方式？！另外，还要考虑清理机制。就是说，下次处理了之后，就移除这个标识
                Boolean returnTaskFlag = runtimeService.getVariable(processInstance.getProcessInstanceId(),
                        String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, t.getTaskDefinitionKey()), Boolean.class);
                if (ObjUtil.notEqual(returnTaskFlag, Boolean.TRUE)) {
                    BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
                    if (bpmnModel == null) {
                        log.error("[processTaskAssigned][taskId({}) 没有找到流程模型]", t.getId());
                        return;
                    }
                    FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, t.getTaskDefinitionKey());
                    Integer assignStartUserHandlerType = BpmnModelUtils.parseAssignStartUserHandlerType(userTaskElement);

                    // 情况一：自动跳过
                    if (ObjectUtils.equalsAny(assignStartUserHandlerType,
                            BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType())) {
                        taskExecutor.execute(() -> getSelf().approveTask(Long.valueOf(t.getAssignee()), new BpmTaskApproveReqVO().setId(t.getId())
                                .setReason(BpmReasonEnum.ASSIGN_START_USER_APPROVE_WHEN_SKIP.getReason())));
                        return;
                    }
                    // 情况二：转交给部门负责人审批
                    if (ObjectUtils.equalsAny(assignStartUserHandlerType,
                            BpmUserTaskAssignStartUserHandlerTypeEnum.TRANSFER_DEPT_LEADER.getType())) {
                        Long startUserId = FlowableUtils.getProcessInstanceStartUserId(processInstance);
                        AdminUserRespDTO startUser = adminUserApi.getUser(startUserId);
                        Assert.notNull(startUser, "提交人({})信息为空", startUserId);
                        DeptRespDTO dept = startUser.getDeptId() != null ? deptApi.getDept(startUser.getDeptId()) : null;
                        Assert.notNull(dept, "提交人({})部门({})信息为空", startUserId, startUser.getDeptId());
                        // 找不到部门负责人的情况下，自动审批通过
                        // noinspection DataFlowIssue
                        if (dept.getLeaderUserId() == null) {
                            taskExecutor.execute(() -> getSelf().approveTask(Long.valueOf(t.getAssignee()), new BpmTaskApproveReqVO().setId(t.getId())
                                    .setReason(BpmReasonEnum.ASSIGN_START_USER_APPROVE_WHEN_DEPT_LEADER_NOT_FOUND.getReason())));
                            return;
                        }
                        // 找得到部门负责人的情况下，修改负责人
                        if (ObjectUtil.notEqual(dept.getLeaderUserId(), startUser.getId())) {
                            getSelf().transferTask(Long.valueOf(t.getAssignee()), new BpmTaskTransferReqVO()
                                    .setId(t.getId()).setAssigneeUserId(dept.getLeaderUserId())
                                    .setReason(BpmReasonEnum.ASSIGN_START_USER_TRANSFER_DEPT_LEADER.getReason()));
                            return;
                        }
                        // 如果部门负责人是自己，还是自己审批吧~
                    }
                }
            }
            // 获取当前任务的FlowElement
            BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(t.getProcessDefinitionId());
            FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, t.getTaskDefinitionKey());

            // 检查是否是会签节点
            boolean IsMultiInstance=false;
            UserTask userTask = (UserTask) flowElement;
            // 判断是否是会签节点
            IsMultiInstance = userTask != null && userTask.getLoopCharacteristics() != null && !((MultiInstanceLoopCharacteristics) userTask.getLoopCharacteristics()).isSequential();
            log.info("[processTaskAssigned][任务({})是否为会签节点: {}]", task.getId(), IsMultiInstance);
            IsMultiInstance = IsMultiInstance || (userTask != null && isInParallelGateway(userTask));
            log.info("[processTaskAssigned][任务({})是否并行包容节点: {}]", task.getId(), isInParallelGateway(userTask));
            System.out.println(IsMultiInstance);
            if (IsMultiInstance) {
                // 记录会签信息
                BpmMultiInstanceMessageDTO multiInstanceMessage = collectMultiInstanceMessage(processInstance, userTask);
                log.info("[processTaskAssigned][会签节点({})已审批人数: {}, 已拒绝人数: {}]",
                        task.getId(),
                        multiInstanceMessage.getApproveUserIds().size(),
                        multiInstanceMessage.getRejectUserIds().size());

            }

            // 注意：需要基于 instance 设置租户编号，避免 Flowable 内部异步时，丢失租户编号

            boolean finalIsMultiInstance = IsMultiInstance;
            FlowableUtils.execute(processInstance.getTenantId(), () -> {
                // 获取流程实例发起人ID，优先使用流程变量中的PROCESS_START_USER_ID
                Long startUserId = FlowableUtils.getProcessInstanceStartUserId(processInstance);
                log.info("[processTaskAssigned][任务({})通知获取发起人ID: {}]", task.getId(), startUserId);
                AdminUserRespDTO startUser = adminUserApi.getUser(startUserId);
                // 在流程中判断是否是委派或者是转办          taskService.setVariableLocal(task.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_IS_DELEGATE,2);
                Integer  is_delegate = (Integer) taskService.getVariableLocal(task.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_IS_DELEGATE);
                log.info("[委派或者是转办    ][----> {}]", is_delegate);
                //如果
                messageService.sendMessageWhenTaskAssigned(BpmTaskConvert.INSTANCE.convert(processInstance, startUser, task), finalIsMultiInstance,is_delegate);
            });
        }
        );
    };


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTaskTimeout(String processInstanceId, String taskDefineKey, Integer handlerType) {
        // 1. 获取流程实例和运行中的任务
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        List<Task> taskList = getRunningTaskListByProcessInstanceId(processInstanceId, true, taskDefineKey);

        log.info("[processTaskTimeout][流程实例({})任务定义({})开始处理超时，找到{}个运行中的任务]",
                processInstanceId, taskDefineKey, taskList.size());

        // 2. 遍历任务执行超时处理
        taskList.forEach(task -> FlowableUtils.execute(task.getTenantId(), () -> {
            BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
            FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
            // 情况一：自动提醒
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.REMINDER.getType())) {
                handleTimeoutReminder(task, processInstance, userTaskElement);
                return;
            }

            // 情况二：自动同意
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType())) {
                approveTask(Long.parseLong(task.getAssignee()),
                        new BpmTaskApproveReqVO().setId(task.getId())
                                .setReason(BpmReasonEnum.TIMEOUT_APPROVE.getReason()));
                return;
            }

            // 情况三：自动拒绝
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.REJECT.getType())) {
                rejectTask(Long.parseLong(task.getAssignee()),
                        new BpmTaskRejectReqVO().setId(task.getId())
                                .setReason(BpmReasonEnum.REJECT_TASK.getReason()));
                return;
            }

            // 情况四：直接自动跳转（不需要提醒）
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.JUMP.getType())) {
                String targetTaskId = BpmnModelUtils.parseTimeoutReturnNodeId(userTaskElement);
                returnTask(Long.parseLong(task.getAssignee()), new BpmTaskReturnReqVO().setId(task.getId())
                        .setTargetTaskDefinitionKey(targetTaskId).setReason(BpmReasonEnum.TIMEOUT_JUMP.getReason()));

            }
        }));
    }



    /**
     * 处理超时提醒逻辑
     */

    @Transactional(rollbackFor = Exception.class)
    public void handleTimeoutReminder(Task task, ProcessInstance processInstance, FlowElement userTaskElement) {
        // 发送任务超时提醒
        messageService.sendMessageWhenTaskTimeout(new BpmMessageSendWhenTaskTimeoutReqDTO()
                .setProcessInstanceId(processInstance.getId())
                .setProcessInstanceName(processInstance.getName())
                .setTaskId(task.getId())
                .setTaskName(task.getName())
                .setAssigneeUserId(Long.parseLong(task.getAssignee())));

        // 检查是否设置了超时跳转目标节点
        String targetTaskId = BpmnModelUtils.parseTimeoutReturnNodeId(userTaskElement);

        if (StrUtil.isNotEmpty(targetTaskId)) {
            String varKey = String.format(PROCESS_INSTANCE_VARIABLE_TIMEOUT_REMIND_COUNT, task.getTaskDefinitionKey());
            Integer reminderCount = runtimeService.getVariable(processInstance.getId(), varKey, Integer.class);
            reminderCount = reminderCount == null ? 1 : reminderCount + 1;
            runtimeService.setVariable(processInstance.getId(), varKey, reminderCount);

            String maxRemindCountStr = BpmnModelUtils.parseExtensionElement(userTaskElement,
                    BpmnModelConstants.USER_TASK_TIMEOUT_JUMP_MAX_REMIND_COUNT);
            int maxRemindCount = StrUtil.isEmpty(maxRemindCountStr) ? 1 : Integer.parseInt(maxRemindCountStr);

            if (reminderCount >= maxRemindCount) {
                log.info("[handleTimeoutReminder][任务({})提醒次数({})达到上限({}), 执行自动跳转到节点({})]",
                        task.getId(), reminderCount, maxRemindCount, targetTaskId);
                // 检查流程实例是否有效
                if (runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count() > 0) {
                    try {
                        runtimeService.removeVariable(processInstance.getId(), varKey);
                    } catch (FlowableObjectNotFoundException e) {
                        log.info("尝试删除变量时发生错误: {}", e.getMessage());
                    }

                    returnTask(Long.parseLong(task.getAssignee()), new BpmTaskReturnReqVO().setId(task.getId())
                            .setProcessInstanceId(processInstance.getId())
                            .setTargetTaskDefinitionKey(targetTaskId)
                            .setReason(BpmReasonEnum.TIMEOUT_JUMP.getReason()));
                } else {
                    log.warn("流程实例({})不存在，无法删除变量", processInstance.getId());
                }
            }
        }
    }


    @Override
    public void processDelayTimerTimeout(String processInstanceId, String taskDefineKey) {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .activityId(taskDefineKey)
                .singleResult();
        if (execution == null) {
            log.error("[processDelayTimerTimeout][processInstanceId({}) activityId({}) 没有找到执行活动]",
                    processInstanceId, taskDefineKey);
            return;
        }

        // 若存在直接触发接收任务，执行后续节点
        FlowableUtils.execute(execution.getTenantId(),
                () -> runtimeService.trigger(execution.getId()));
    }
    @Override
    public List<Task> getRunningTasksByProcessInstanceIds(Collection<String> processInstanceIds) {
        if (CollUtil.isEmpty(processInstanceIds)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).active()
                .includeTaskLocalVariables().list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferRunningTasks(Long userId, BpmTaskTransferAllReqVO reqVO) {
        if (ObjectUtil.equals(reqVO.getFromUserId(), reqVO.getToUserId())) {
            throw exception(TASK_TRANSFER_FAIL_USER_REPEAT);
        }
        AdminUserRespDTO toUser = adminUserApi.getUser(reqVO.getToUserId());
        if (toUser == null) {
            throw exception(TASK_TRANSFER_FAIL_USER_NOT_EXISTS);
        }
        // 查询指定审批人的所有运行中任务
        List<Task> taskList = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(reqVO.getFromUserId()))
                .active()
                .list();
        if (StrUtil.isNotBlank(reqVO.getModelId())) {
            taskList = taskList.stream().filter(task -> {
                BpmProcessDefinitionInfoDO info = bpmProcessDefinitionService.getProcessDefinitionInfo(task.getProcessDefinitionId());
                if (info == null || !reqVO.getModelId().equals(info.getModelId())) {
                    return false;
                }
                if (reqVO.getVersion() != null) {
                    ProcessDefinition definition = bpmProcessDefinitionService.getProcessDefinition(task.getProcessDefinitionId());
                    return definition != null && definition.getVersion() == reqVO.getVersion();
                }
                return true;
            }).toList();
        }
        if (CollUtil.isEmpty(taskList)) {
            return;
        }
        for (Task task : taskList) {
            BpmTaskTransferReqVO item = new BpmTaskTransferReqVO();
            item.setId(task.getId());
            item.setAssigneeUserId(reqVO.getToUserId());
            item.setReason(reqVO.getReason());
            item.setManagerial(true);
            // 使用自身代理，确保事务和AOP生效
            getSelf().transferTask(userId, item);
        }
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private BpmTaskServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

    /**
     * 检查两个节点是否在同一个流程范围内（主流程或同一个子流程）
     *
     * @param source 源节点
     * @param target 目标节点
     * @param bpmnModel 流程模型
     * @return 是否在同一个流程范围内
     */
    private boolean isInSameProcessScope(FlowElement source, FlowElement target, BpmnModel bpmnModel) {
        if (source == null || target == null) {
            return false;
        }

        // 获取节点所属的流程范围
        FlowElementsContainer sourceContainer = source.getParentContainer();
        FlowElementsContainer targetContainer = target.getParentContainer();

        // 如果两个节点在同一个容器中，则它们在同一个流程范围内
        return sourceContainer == targetContainer;
    }

    /**
     * 在事务完成后执行指定的回调逻辑
     *
     * @param task     任务信息
     * @param callback 回调逻辑
     */
    private void registerAfterTransaction(Task task, Consumer<Task> callback) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int transactionStatus) {
                // 回滚情况，直接返回
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_ROLLED_BACK)) {
                    return;
                }
                // 特殊情况：第一个 task 【自动通过】时，第二个任务设置审批人时 transactionStatus 会为 STATUS_UNKNOWN，不知道啥原因
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_UNKNOWN)
                        && getTask(task.getId()) == null) {
                    return;
                }
                callback.accept(task);
            }
        });
    }

    /**
     * 收集会签节点的审批信息
     *
     * @param instance 流程实例
     * @param userTask 用户任务
     * @return 会签节点信息DTO
     */
    private BpmMultiInstanceMessageDTO collectMultiInstanceMessage(ProcessInstance instance, UserTask userTask) {
        BpmMultiInstanceMessageDTO message = new BpmMultiInstanceMessageDTO();
        message.setProcessInstanceId(instance.getId());
        message.setProcessInstanceName(instance.getName());
        message.setActivityId(userTask.getId());
        message.setActivityName(userTask.getName());

        // 添加详细日志
        log.info("[collectMultiInstanceMessage][开始查询会签节点({})的任务信息，流程实例: {}]",
                userTask.getId(), instance.getId());
        // 1. 查询所有已完成的历史任务
        List<HistoricTaskInstance> finishedTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey(userTask.getId())
                .finished()
                .list();

        // 2. 查询所有正在运行的任务
        List<Task> runningTasks = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey(userTask.getId())
                .list();

        log.info("[collectMultiInstanceMessage][会签节点({})查询结果：已完成任务数: {}, 运行中任务数: {}]",
                userTask.getId(), finishedTasks.size(), runningTasks.size());

        // 收集审批人和拒绝人
        List<Long> approveUserIds = new ArrayList<>();
        List<Long> rejectUserIds = new ArrayList<>();

        // 3. 处理已完成的任务
        for (HistoricTaskInstance task : finishedTasks) {
            Integer status = FlowableUtils.getTaskStatus(task);
            String deleteReason = task.getDeleteReason();

            log.info("[collectMultiInstanceMessage][历史任务({})，状态: {}, 删除原因: {}]",
                    task.getId(), status, deleteReason);

            // 3.1 优先使用任务变量中的状态
            if (status != null) {
                if (BpmTaskStatusEnum.isApproveStatus(status)) {
                    approveUserIds.add(Long.valueOf(task.getAssignee()));
                    log.info("[collectMultiInstanceMessage][历史任务({})状态为审批通过]", task.getId());
                } else if (BpmTaskStatusEnum.isRejectStatus(status)) {
                    rejectUserIds.add(Long.valueOf(task.getAssignee()));
                    log.info("[collectMultiInstanceMessage][历史任务({})状态为审批拒绝]", task.getId());
                }
            }
            // 3.2 如果状态为空，尝试从删除原因判断
            else if (deleteReason != null) {
                if (deleteReason.contains("completed")) {
                    approveUserIds.add(Long.valueOf(task.getAssignee()));
                    log.info("[collectMultiInstanceMessage][历史任务({})根据deleteReason判断为通过]", task.getId());
                } else if (deleteReason.contains("rejected")) {
                    rejectUserIds.add(Long.valueOf(task.getAssignee()));
                    log.info("[collectMultiInstanceMessage][历史任务({})根据deleteReason判断为拒绝]", task.getId());
                }
            }
        }

        // 4. 更新消息对象
        message.setApproveUserIds(approveUserIds);
        message.setRejectUserIds(rejectUserIds);
//        message.setReason(FlowableUtils.getProcessInstanceReason((HistoricProcessInstance) instance));

        // 5. 最终汇总日志
        log.info("[collectMultiInstanceMessage][会签节点({})统计结果：审批通过人数: {}, 审批拒绝人数: {}]",
                userTask.getId(), approveUserIds.size(), rejectUserIds.size());

        return message;
    }






}





