package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;

import java.util.Map;

/**
 * 支持租户的变量容器
 * 
 * 通过装饰器模式包装 MapDelegateVariableContainer，
 * 重写 getTenantId() 方法以返回正确的租户ID
 *
 * @author AI Assistant
 */
@Slf4j
public class TenantAwareVariableContainer implements VariableContainer {

    private final MapDelegateVariableContainer delegate;
    private final String tenantId;

    public TenantAwareVariableContainer(Map<String, Object> variables, VariableContainer parent) {
        this.delegate = new MapDelegateVariableContainer(variables, parent);
        this.tenantId = getCurrentTenantId();
    }

    /**
     * 获取当前租户ID
     * 
     * @return 当前租户ID，如果没有租户上下文则返回null
     */
    private String getCurrentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? String.valueOf(tenantId) : null;
    }

    @Override
    public Object getVariable(String variableName) {
        Object value = delegate.getVariable(variableName);
        
        // 添加调试日志，特别关注PROCESS_START_USER_ID
        if ("PROCESS_START_USER_ID".equals(variableName) || variableName != null && variableName.contains("USER_ID")) {
            log.info("=== 变量调试信息 ===");
            log.info("变量名: {}", variableName);
            log.info("变量值: {}", value);
            log.info("变量类型: {}", value != null ? value.getClass().getSimpleName() : "null");
            log.info("变量值toString: {}", value != null ? value.toString() : "null");
            if (value != null) {
                log.info("是否为数字类型: {}", value instanceof Number);
                log.info("是否为字符串类型: {}", value instanceof String);
                if (value instanceof Number) {
                    log.info("数字值: {}", ((Number) value).longValue());
                    // 对于用户ID相关的变量，如果是数字类型，转换为字符串返回
                    log.info("将数字类型的用户ID转换为字符串");
                    return value.toString();
                }
            }
            log.info("===================");
        }
        
        return value;
    }

    @Override
    public boolean hasVariable(String variableName) {
        return delegate.hasVariable(variableName);
    }

    @Override
    public void setVariable(String variableName, Object value) {
        delegate.setVariable(variableName, value);
    }

    @Override
    public void setTransientVariable(String variableName, Object variableValue) {
        delegate.setTransientVariable(variableName, variableValue);
    }

    @Override
    public String getTenantId() {
        // 返回当前租户ID，而不是delegate的tenantId（通常为null）
        return tenantId;
    }

    @Override
    public String toString() {
        return "TenantAwareVariableContainer[delegate=" + delegate + ", tenantId=" + tenantId + "]";
    }
} 