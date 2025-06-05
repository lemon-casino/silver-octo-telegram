package cn.iocoder.yudao.module.bpm.framework.flowable.core.enums;

import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;

/**
 * BPMN XML 常量信息
 *
 * @author 芋道源码
 */
public interface BpmnModelConstants {

    String BPMN_FILE_SUFFIX = ".bpmn";

    /**
     * 特殊网关开始节点后缀
     */
    String SPECIAL_GATEWAY_BEGIN_SUFFIX = "_begin";
    /**
     * 特殊网关结束节点后缀
     */
    String SPECIAL_GATEWAY_END_SUFFIX = "_end";
    /**
     * BPMN 中的命名空间
     */
    String NAMESPACE = "http://flowable.org/bpmn";

    /**
     * BPMN UserTask 的扩展属性，用于标记候选人策略
     */
    String USER_TASK_CANDIDATE_STRATEGY = "candidateStrategy";
    /**
     * BPMN UserTask 的扩展属性，用于标记候选人参数
     */
    String USER_TASK_CANDIDATE_PARAM = "candidateParam";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记边界事件类型
     */
    String BOUNDARY_EVENT_TYPE = "boundaryEventType";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务超时执行动作
     */
    String USER_TASK_TIMEOUT_HANDLER_TYPE = "timeoutHandlerType";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务超时执行动作参数
     */

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务的审批人与发起人相同时，对应的处理类型
     */
    String USER_TASK_ASSIGN_START_USER_HANDLER_TYPE = "assignStartUserHandlerType";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务的空处理类型
     */
    String USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE = "assignEmptyHandlerType";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务的空处理的指定用户编号数组
     */
    String USER_TASK_ASSIGN_USER_IDS = "assignEmptyUserIds";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务拒绝处理类型
     */
    String USER_TASK_REJECT_HANDLER_TYPE = "rejectHandlerType";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务拒绝后的退回的任务 Id
     */
    String USER_TASK_REJECT_RETURN_TASK_ID = "rejectReturnTaskId";

    /**
     * BPMN UserTask 的扩展属性，用于标记用户任务的审批类型
     */
    String USER_TASK_APPROVE_TYPE = "approveType";

    /**
     * BPMN UserTask 的扩展属性，用于标记用户任务的审批方式
     */
    String USER_TASK_APPROVE_METHOD = "approveMethod";

    /**
     * BPMN ExtensionElement 流程表单字段权限元素, 用于标记字段权限
     */
    String FORM_FIELD_PERMISSION_ELEMENT = "fieldsPermission";

    /**
     * BPMN ExtensionElement Attribute, 用于标记表单字段
     */
    String FORM_FIELD_PERMISSION_ELEMENT_FIELD_ATTRIBUTE = "field";
    /**
     * BPMN ExtensionElement Attribute, 用于标记表单权限
     */
    String FORM_FIELD_PERMISSION_ELEMENT_PERMISSION_ATTRIBUTE = "permission";

    /**
     * BPMN ExtensionElement 操作按钮设置元素, 用于审批节点操作按钮设置
     */
    String BUTTON_SETTING_ELEMENT = "buttonsSetting";

    /**
     * BPMN ExtensionElement Attribute, 用于标记按钮编号
     */
    String BUTTON_SETTING_ELEMENT_ID_ATTRIBUTE = "id";

    /**
     * BPMN ExtensionElement Attribute, 用于标记按钮显示名称
     */
    String BUTTON_SETTING_ELEMENT_DISPLAY_NAME_ATTRIBUTE = "displayName";

    /**
     * BPMN ExtensionElement Attribute, 用于标记按钮是否启用
     */
    String BUTTON_SETTING_ELEMENT_ENABLE_ATTRIBUTE = "enable";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记触发器的类型
     */
    String TRIGGER_TYPE = "triggerType";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记触发器参数
     */
    String TRIGGER_PARAM = "triggerParam";

    /**
     * BPMN Start Event Node Id
     */
    String START_EVENT_NODE_ID = "StartEvent";

    /**
     * 发起人节点 ID
     */
    String START_USER_NODE_ID = "StartUserNode";

    /**
     * 是否需要签名
     */
    String SIGN_ENABLE = "signEnable";

    /**
     * 审批意见是否必填
     */
    String REASON_REQUIRE = "reasonRequire";
    /**
     * 是否抄送本人
     */
    String COPY_SELF = "copySelf";
    /**
     * 节点类型
     *
     * 目前只有 {@link BpmModelTypeEnum#SIMPLE} 的 UserTask 节点会设置该属性，用于区分是审批节点、还是办理节点
     */
    String NODE_TYPE = "nodeType";

    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务超时返回的节点 Id
     */
    String USER_TASK_TIMEOUT_JUMP_TASK_ID = "timeoutReturnNodeId";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记用户任务超时跳转前的最大提醒次数
     */
    String USER_TASK_TIMEOUT_JUMP_MAX_REMIND_COUNT = "maxRemindCount";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记计算超时时使用的工作时间类型
     */
    String USER_TASK_WORK_TIME_TYPE = "workTimeType";
    /**
     * BPMN ExtensionElement 的扩展属性，用于标记是否按照工作时间计算超时
     */
    String USER_TASK_WORK_TIME_ENABLE = "workTimeEnable";


}
