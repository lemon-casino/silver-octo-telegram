package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 流程实例的创建 Request VO")
@Data@ToString
public class BpmProcessInstanceCreateReqVO {

    @Schema(description = "流程定义的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "流程定义编号不能为空")
    private String processDefinitionId;

    @Schema(description = "变量实例（动态表单）")
    private Map<String, Object> variables;

    @Schema(description = "发起人自选审批人 Map", example = "{taskKey1: [1, 2]}")
    private Map<String, List<Long>> startUserSelectAssignees;

    // 提供的用户id 如果有填写 则发起人是对应的用户id的身份发起
    @Schema(description = "发起人refreshToken", example = "0084fe4dgt516d3cg516d3cdf5c949ac949682a")
    private String refreshToken = "";

    // 重写 getVariables 方法进行处理
    public Map<String, Object> getVariables() {
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String variableKey = entry.getKey();
                Object variableValue = entry.getValue();

                if (variableValue instanceof String arrayStr) {

                    // 检查字符串是否是数组格式
                    if (arrayStr.startsWith("[") && arrayStr.endsWith("]")) {
                        // 去掉首尾的方括号
                        String content = arrayStr.substring(1, arrayStr.length() - 1);

                        // 分割字符串（如果以逗号分隔）
                        String[] items = content.split(",");

                        // 创建一个列表来存储结果
                        List<String> resultList = new ArrayList<>();
                        for (String item : items) {
                            resultList.add(item.trim());  // 去掉前后空格
                        }

                        // 将结果更新回原始变量映射
                        variables.put(variableKey, resultList);
                    }
                }
            }
        }
        return variables;
    }
}