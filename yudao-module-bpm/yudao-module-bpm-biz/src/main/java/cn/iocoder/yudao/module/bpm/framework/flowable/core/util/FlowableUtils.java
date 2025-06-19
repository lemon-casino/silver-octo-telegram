package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.form.BpmFormFieldVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import lombok.SneakyThrows;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskInfo;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * Flowable 相关的工具方法
 *
 * @author 芋道源码
 */
public class FlowableUtils {

    // ========== User 相关的工具方法 ==========

    public static void setAuthenticatedUserId(Long userId) {
        Authentication.setAuthenticatedUserId(String.valueOf(userId));
    }

    public static void clearAuthenticatedUserId() {
        Authentication.setAuthenticatedUserId(null);
    }

    public static <V> V executeAuthenticatedUserId(Long userId, Callable<V> callable) {
        setAuthenticatedUserId(userId);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clearAuthenticatedUserId();
        }
    }

    public static String getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? String.valueOf(tenantId) : ProcessEngineConfiguration.NO_TENANT_ID;
    }

    public static void execute(String tenantIdStr, Runnable runnable) {
        if (ObjectUtil.isEmpty(tenantIdStr)
                || Objects.equals(tenantIdStr, ProcessEngineConfiguration.NO_TENANT_ID)) {
            runnable.run();
        } else {
            Long tenantId = Long.valueOf(tenantIdStr);
            TenantUtils.execute(tenantId, runnable);
        }
    }

    @SneakyThrows
    public static <V> V execute(String tenantIdStr, Callable<V> callable) {
        if (ObjectUtil.isEmpty(tenantIdStr)
                || Objects.equals(tenantIdStr, ProcessEngineConfiguration.NO_TENANT_ID)) {
            return callable.call();
        } else {
            Long tenantId = Long.valueOf(tenantIdStr);
            return TenantUtils.execute(tenantId, callable);
        }
    }

    // ========== Execution 相关的工具方法 ==========

    /**
     * 格式化多实例（并签、或签）的 collectionVariable 变量（多实例对应的多审批人列表）
     *
     * @param activityId 活动编号
     * @return collectionVariable 变量
     */
    public static String formatExecutionCollectionVariable(String activityId) {
        return activityId + "_assignees";
    }

    /**
     * 格式化多实例（并签、或签）的 collectionElementVariable 变量（当前实例对应的一个审批人）
     *
     * @param activityId 活动编号
     * @return collectionElementVariable 变量
     */
    public static String formatExecutionCollectionElementVariable(String activityId) {
        return activityId + "_assignee";
    }

    // ========== ProcessInstance 相关的工具方法 ==========

    public static Integer getProcessInstanceStatus(ProcessInstance processInstance) {
        return getProcessInstanceStatus(processInstance.getProcessVariables());
    }

    public static Integer getProcessInstanceStatus(HistoricProcessInstance processInstance) {
        return getProcessInstanceStatus(processInstance.getProcessVariables());
    }

    /**
     * 获得流程实例的状态
     *
     * @param processVariables 流程实例的 variables
     * @return 状态
     */
    private static Integer getProcessInstanceStatus(Map<String, Object> processVariables) {
        return (Integer) processVariables.get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
    }

    /**
     * 获得流程实例的审批原因
     *
     * @param processInstance 流程实例
     * @return 审批原因
     */
    public static String getProcessInstanceReason(HistoricProcessInstance processInstance) {
        return (String) processInstance.getProcessVariables().get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON);
    }

    /**
     * 获得流程实例的表单
     *
     * @param processInstance 流程实例
     * @return 表单
     */
    public static Map<String, Object> getProcessInstanceFormVariable(ProcessInstance processInstance) {
        Map<String, Object> processVariables = new HashMap<>(processInstance.getProcessVariables());
        return filterProcessInstanceFormVariable(processVariables);
    }

    /**
     * 获得流程实例的表单
     *
     * @param processInstance 流程实例
     * @return 表单
     */
    public static Map<String, Object> getProcessInstanceFormVariable(HistoricProcessInstance processInstance) {
        Map<String, Object> processVariables = new HashMap<>(processInstance.getProcessVariables());
        return filterProcessInstanceFormVariable(processVariables);
    }

    /**
     * 过滤流程实例的表单
     *
     * 为什么要过滤？目前使用 processVariables 存储所有流程实例的拓展字段，需要过滤掉一部分的系统字段，从而实现表单的展示
     *
     * @param processVariables 流程实例的 variables
     * @return 过滤后的表单
     */
    public static Map<String, Object> filterProcessInstanceFormVariable(Map<String, Object> processVariables) {
        processVariables.remove(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
        return processVariables;
    }

    /**
     * 获得流程实例的发起用户选择的审批人 Map
     *
     * @param processInstance 流程实例
     * @return 发起用户选择的审批人 Map
     */
    public static Map<String, List<Long>> getStartUserSelectAssignees(ProcessInstance processInstance) {
        return processInstance != null ? getStartUserSelectAssignees(processInstance.getProcessVariables()) : null;
    }

    /**
     * 获得流程实例的发起用户选择的审批人 Map
     *
     * @param processVariables 流程变量
     * @return 发起用户选择的审批人 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Long>> getStartUserSelectAssignees(Map<String, Object> processVariables) {
        if (processVariables == null) {
            return null;
        }
        return (Map<String, List<Long>>) processVariables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES);
    }

    /**
     * 获得流程实例的摘要
     *
     * 仅有 {@link BpmModelFormTypeEnum#getType()} 表单，才有摘要。
     * 原因是，只有它才有表单项的配置，从而可以根据配置，展示摘要。
     *
     * @param processDefinitionInfo 流程定义
     * @param processVariables      流程实例的 variables
     * @return 摘要
     */
    public static List<KeyValue<String, String>> getSummary(BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                            Map<String, Object> processVariables) {
        // 只有流程表单才会显示摘要！
        if (ObjectUtil.isNull(processDefinitionInfo)
                || !BpmModelFormTypeEnum.NORMAL.getType().equals(processDefinitionInfo.getFormType())) {
            return null;
        }

        // 解析表单配置
        Map<String, BpmFormFieldVO> formFieldsMap = parseFormFieldsMap(processDefinitionInfo.getFormFields());

        // 情况一：当自定义了摘要
        if (ObjectUtil.isNotNull(processDefinitionInfo.getSummarySetting())
                && Boolean.TRUE.equals(processDefinitionInfo.getSummarySetting().getEnable())) {
            return convertList(processDefinitionInfo.getSummarySetting().getSummary(), item -> {
                BpmFormFieldVO formField = formFieldsMap.get(item);
                if (formField != null) {
                    Object value = processVariables.get(item);
                    return new KeyValue<String, String>(formField.getTitle(),
                            value != null ? value.toString() : "");
                }
                return null;
            });
        }

        // 情况二：默认摘要展示前三个表单字段
        return formFieldsMap.entrySet().stream()
                .limit(3)
                .map(entry -> {
                    Object value = processVariables.getOrDefault(entry.getValue().getField(), "");
                    return new KeyValue<>(
                            entry.getValue().getTitle(),
                            value != null ? value.toString() : ""
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 获得流程实例的发起人ID
     * 
     * 优先使用流程变量中存储的PROCESS_START_USER_ID，其次使用processInstance.getStartUserId()
     *
     * @param processInstance 流程实例
     * @return 流程发起人ID
     */
    public static Long getProcessInstanceStartUserId(ProcessInstance processInstance) {
        if (processInstance == null) {
            return null;
        }
        
        // 优先从流程变量中获取发起人ID
        Object startUserIdVar = processInstance.getProcessVariables().get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID);
        if (startUserIdVar != null) {
            return Long.valueOf(startUserIdVar.toString());
        }
        
        // 如果流程变量中没有，则使用processInstance.getStartUserId()
        return NumberUtils.parseLong(processInstance.getStartUserId());
    }
    
    /**
     * 获得流程实例的发起人ID
     *
     * @param processInstance 历史流程实例
     * @return 流程发起人ID
     */
    public static Long getProcessInstanceStartUserId(HistoricProcessInstance processInstance) {
        if (processInstance == null) {
            return null;
        }
        
        // 优先从流程变量中获取发起人ID
        Object startUserIdVar = processInstance.getProcessVariables().get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID);
        if (startUserIdVar != null) {
            return Long.valueOf(startUserIdVar.toString());
        }
        
        // 如果流程变量中没有，则使用processInstance.getStartUserId()
        return NumberUtils.parseLong(processInstance.getStartUserId());
    }

    // ========== Task 相关的工具方法 ==========

    /**
     * 获得任务的状态
     *
     * @param task 任务
     * @return 状态
     */
    public static Integer getTaskStatus(TaskInfo task) {
        return (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
    }

    /**
     * 获得任务的审批原因
     *
     * @param task 任务
     * @return 审批原因
     */
    public static String getTaskReason(TaskInfo task) {
        return (String) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_REASON);
    }

    /**
     * 获得任务的签名图片 URL
     *
     * @param task 任务
     * @return 签名图片 URL
     */
    public static String getTaskSignPicUrl(TaskInfo task) {
        return (String) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_SIGN_PIC_URL);
    }

    /**
     * 获得任务的表单
     *
     * @param task 任务
     * @return 表单
     */
    public static Map<String, Object> getTaskFormVariable(TaskInfo task) {
        Map<String, Object> formVariables = new HashMap<>(task.getTaskLocalVariables());
        filterTaskFormVariable(formVariables);
        return formVariables;
    }

    /**
     * 过滤任务的表单
     *
     * 为什么要过滤？目前使用 taskLocalVariables 存储所有任务的拓展字段，需要过滤掉一部分的系统字段，从而实现表单的展示
     *
     * @param taskLocalVariables 任务的 taskLocalVariables
     * @return 过滤后的表单
     */
    public static Map<String, Object> filterTaskFormVariable(Map<String, Object> taskLocalVariables) {
        taskLocalVariables.remove(BpmnVariableConstants.TASK_VARIABLE_STATUS);
        taskLocalVariables.remove(BpmnVariableConstants.TASK_VARIABLE_REASON);
        return taskLocalVariables;
    }

    /**
     * 处理表单变量，确保数值类型变量以字符串形式存储
     * 
     * 为什么要处理？大数值（比如超长的数字ID）存储时可能会被截断，我们统一将数值类型转为字符串存储
     * 
     * @param variables 表单变量
     * @return 处理后的表单变量
     */
    public static Map<String, Object> processFormVariables(Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>(variables.size());
        // 处理数值型变量，确保它们以字符串形式存储，避免大数值被截断
        variables.forEach((key, value) -> {
            if (value != null) {
                if (value instanceof Number) {
                    // 数值类型直接转字符串
                    String stringValue = value.toString();
                    result.put(key, stringValue);

                } else if (value instanceof String && cn.hutool.core.util.NumberUtil.isNumber(value.toString())) {
                    // 字符串形式的数值，保持字符串
                    result.put(key, value.toString());

                } else {
                    // 其它类型保持不变
                    result.put(key, value);
                }
            } else {
                result.put(key, null);
            }
        });
        return result;
    }

    // ========== Expression 相关的工具方法 ==========

    private static Object getExpressionValue(VariableContainer variableContainer, String expressionString,
                                             ProcessEngineConfigurationImpl processEngineConfiguration) {
        assert processEngineConfiguration != null;
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
        assert expressionManager != null;
        Expression expression = expressionManager.createExpression(expressionString);
        return expression.getValue(variableContainer);
    }

    public static Object getExpressionValue(VariableContainer variableContainer, String expressionString) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration();
        if (processEngineConfiguration != null) {
            return getExpressionValue(variableContainer, expressionString, processEngineConfiguration);
        }
        // 如果 ProcessEngineConfigurationImpl 获取不到，则需要通过 ManagementService 来获取
        ManagementService managementService = SpringUtil.getBean(ManagementService.class);
        assert managementService != null;
        return managementService.executeCommand(context ->
                getExpressionValue(variableContainer, expressionString, CommandContextUtil.getProcessEngineConfiguration()));
    }

    public static Object getExpressionValue(Map<String, Object> variable, String expressionString) {
        VariableContainer variableContainer = new MapDelegateVariableContainer(variable, VariableContainer.empty());
        return getExpressionValue(variableContainer, expressionString);
    }

    /**
     * 获得流程实例的所有摘要字段
     *
     * 仅有 {@link BpmModelFormTypeEnum#NORMAL} 表单，才有摘要。
     * 返回所有表单字段对应的摘要内容
     *
     * @param processDefinitionInfo 流程定义
     * @param processVariables      流程实例的 variables
     * @return 全部摘要字段
     */
    public static List<KeyValue<String, String>> getSummaryAll(BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                               Map<String, Object> processVariables) {
        if (ObjectUtil.isNull(processDefinitionInfo)
                || !BpmModelFormTypeEnum.NORMAL.getType().equals(processDefinitionInfo.getFormType())) {
            return null;
        }

        Map<String, BpmFormFieldVO> formFieldsMap = parseFormFieldsMap(processDefinitionInfo.getFormFields());

        return formFieldsMap.values().stream()
                .map(bpmFormFieldVO -> {
                    String fieldKey = bpmFormFieldVO.getField();
                    if (fieldKey == null) {
                        return new KeyValue<>(bpmFormFieldVO.getTitle(), "");
                    }
                    Object value = processVariables.get(fieldKey);
                    Object display = buildDisplayValue(value, formFieldsMap);
                    String displayStr = display instanceof Collection || display instanceof Map
                            ? JsonUtils.toJsonString(display)
                            : Optional.ofNullable(display).map(Object::toString).orElse("");
                    return new KeyValue<>(bpmFormFieldVO.getTitle(), displayStr);
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建展示用的表单值，解决子表单等复杂结构的展示问题
     *
     * @param value          表单值
     * @param formFieldsMap   表单字段配置
     * @return 转换后的展示对象
     */
    @SuppressWarnings("unchecked")
    private static Object buildDisplayValue(Object value, Map<String, BpmFormFieldVO> formFieldsMap) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String title = Optional.ofNullable(formFieldsMap.get(key)).map(BpmFormFieldVO::getTitle).orElse(key);
                result.put(title, buildDisplayValue(entry.getValue(), formFieldsMap));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : collection) {
                list.add(buildDisplayValue(item, formFieldsMap));
            }
            return list;
        }
        return value;
    }

    /**
     * 解析流程定义中配置的表单字段，包括子表单的字段
     */
    @SuppressWarnings("unchecked")
    private static Map<String, BpmFormFieldVO> parseFormFieldsMap(List<String> formFields) {
        Map<String, BpmFormFieldVO> map = new LinkedHashMap<>();
        if (CollUtil.isEmpty(formFields)) {
            return map;
        }
        for (String fieldStr : formFields) {
            Object obj = JsonUtils.parseObject(fieldStr, Object.class);
            if (obj instanceof Map<?, ?> m) {
                collectFieldTitles((Map<String, Object>) m, map);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void collectFieldTitles(Map<String, Object> node, Map<String, BpmFormFieldVO> map) {
        Object field = node.getOrDefault("field", node.get("vModel"));
        Object title = node.getOrDefault("title", node.get("label"));
        Object type = node.get("type");
        if (field instanceof String && title instanceof String) {
            if (!map.containsKey(field)) {
                BpmFormFieldVO vo = new BpmFormFieldVO();
                vo.setField((String) field);
                vo.setTitle((String) title);
                if (type != null) {
                    vo.setType(type.toString());
                }
                map.put((String) field, vo);
            }
        }
        for (Object value : node.values()) {
            if (value instanceof Map<?, ?>) {
                collectFieldTitles((Map<String, Object>) value, map);
            } else if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> itemMap) {
                        collectFieldTitles((Map<String, Object>) itemMap, map);
                    }
                }
            }
        }
    }
}
