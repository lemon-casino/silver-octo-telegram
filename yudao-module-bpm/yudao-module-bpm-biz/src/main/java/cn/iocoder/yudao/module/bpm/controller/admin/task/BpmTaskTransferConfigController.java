package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskTransferConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 任务转办配置")
@RestController
@RequestMapping("/bpm/task-transfer-config")
@Validated
public class BpmTaskTransferConfigController {

    @Resource
    private BpmTaskTransferConfigService transferConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建转办配置")
    @PreAuthorize("@ss.hasPermission('bpm:task-transfer-config:create')")
    public CommonResult<Long> create(@Valid @RequestBody BpmTaskTransferConfigSaveReqVO createReqVO) {
        return success(transferConfigService.createTaskTransferConfig(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新转办配置")
    @PreAuthorize("@ss.hasPermission('bpm:task-transfer-config:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody BpmTaskTransferConfigSaveReqVO updateReqVO) {
        transferConfigService.updateTaskTransferConfig(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除转办配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task-transfer-config:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        transferConfigService.deleteTaskTransferConfig(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得转办配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task-transfer-config:query')")
    public CommonResult<BpmTaskTransferConfigRespVO> get(@RequestParam("id") Long id) {
        BpmTaskTransferConfigDO config = transferConfigService.getTaskTransferConfig(id);
        return success(BeanUtils.toBean(config, BpmTaskTransferConfigRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得转办配置分页")
    @PreAuthorize("@ss.hasPermission('bpm:task-transfer-config:query')")
    public CommonResult<PageResult<BpmTaskTransferConfigRespVO>> page(@Valid BpmTaskTransferConfigPageReqVO pageVO) {
        PageResult<BpmTaskTransferConfigDO> pageResult = transferConfigService.getTaskTransferConfigPage(pageVO);
        return success(BeanUtils.toBean(pageResult, BpmTaskTransferConfigRespVO.class));
    }
}
