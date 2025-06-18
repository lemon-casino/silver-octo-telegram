package cn.iocoder.yudao.module.bpm.framework.flowable.core.el;

import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.function.AbstractFlowableVariableExpressionFunction;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.VariableTypeUtils;

import java.util.Objects;

/**
 * 自定义 equals 表达式函数，避免变量不存在时抛出异常
 */
@Component
public class EqualsExpressionFunction extends AbstractFlowableVariableExpressionFunction {

    public EqualsExpressionFunction() {
        super("equals");
    }

    /**
     * 判断变量与参数是否相等，变量不存在时返回 false
     *
     * @param variableContainer 变量容器
     * @param variableName      变量名
     * @param paramValue        参数值
     * @return 是否相等
     */
    public static boolean equals(VariableContainer variableContainer, String variableName, Object paramValue) {
        Object variable = variableContainer.getVariable(variableName);
        if (variable == null) {
            return false;
        }
        Object converted = VariableTypeUtils.convertByType(variable, paramValue);
        return Objects.equals(variable, converted);
    }
}
