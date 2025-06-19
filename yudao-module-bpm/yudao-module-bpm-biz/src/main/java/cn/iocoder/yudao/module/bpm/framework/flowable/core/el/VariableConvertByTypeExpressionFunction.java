package cn.iocoder.yudao.module.bpm.framework.flowable.core.el;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.VariableTypeUtils;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.function.AbstractFlowableVariableExpressionFunction;
import org.springframework.stereotype.Component;

/**
 * 根据流程变量 variable 的类型，转换参数的值
 *
 * 目前用于 ConditionNodeConvert 的 buildConditionExpression 方法中
 *
 * @author jason
 */
@Component
public class VariableConvertByTypeExpressionFunction extends AbstractFlowableVariableExpressionFunction {

    public VariableConvertByTypeExpressionFunction() {
        super("convertByType");
    }

    /**
     * 根据变量的类型，转换参数的值
     *
     * @param variableContainer 变量的容器
     * @param variableName      变量名
     * @param paramValue        参数值
     * @return 转换后的值
     */
    public static Object convertByType(VariableContainer variableContainer, String variableName, Object paramValue) {
        try {
            if (variableContainer == null || variableName == null || paramValue == null) {
                return false;
            }
            Object variable = variableContainer.getVariable(variableName);
            if (variable == null) {
                return false;
            }
            return VariableTypeUtils.convertByType(variable, paramValue);
        } catch (Exception ignored) {
            // If any unexpected exception occurs, treat as not matched
            return false;
        }
    }
}
