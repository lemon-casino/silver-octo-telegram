package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 流程模型版本及表单项 Response VO
 */
@Data
public class BpmModelVersionFormRespVO {

    @Schema(description = "流程模型编号", example = "a2c5eee0")
    private String id;

    @Schema(description = "流程模型名称", example = "请假流程")
    private String name;

    @Schema(description = "流程版本及表单项列表")
    private List<VersionInfo> versions;

    @Data
    public static class VersionInfo {
        @Schema(description = "流程版本号", example = "1")
        private Integer version;

        @Schema(description = "表单项数组")
        private List<String> formFields;
    }
}
