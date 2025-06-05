package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ComplexGateway;
import org.flowable.bpmn.model.EventSubProcess;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

    /**
     * 扩展的 Flowable 工具类，提供流程跳转等能力
     */
public class FlowableJumpUtils {

        public static final String SPECIAL_GATEWAY_BEGIN_SUFFIX = BpmnModelConstants.SPECIAL_GATEWAY_BEGIN_SUFFIX;
        public static final String SPECIAL_GATEWAY_END_SUFFIX = BpmnModelConstants.SPECIAL_GATEWAY_END_SUFFIX;
        public static final String SPECIAL_GATEWAY_JOIN_SUFFIX = BpmnModelConstants.SPECIAL_GATEWAY_JOIN_SUFFIX;
        public static final String FLOWABLE_NAMESPACE = BpmnModelConstants.NAMESPACE;

        // ========== User 相关 ==========

        public static void setAuthenticatedUserId(Long userId) {
            Authentication.setAuthenticatedUserId(String.valueOf(userId));
        }

        public static void setAuthenticatedUserId(String bpmUserId) {
            Authentication.setAuthenticatedUserId(bpmUserId);
        }

        public static void clearAuthenticatedUserId() {
            Authentication.setAuthenticatedUserId(null);
        }

        // ========== BPMN 相关 ==========

        public static <T extends FlowElement> List<T> getBpmnModelElements(BpmnModel model, Class<T> clazz) {
            List<T> result = new ArrayList<>();
            model.getProcesses().forEach(process -> {
                process.getFlowElements().forEach(flowElement -> {
                    if (clazz.isAssignableFrom(flowElement.getClass())) {
                        result.add((T) flowElement);
                    }
                });
            });
            return result;
        }

        public static boolean equals(BpmnModel oldModel, BpmnModel newModel) {
            return Arrays.equals(getBpmnBytes(oldModel), getBpmnBytes(newModel));
        }

        public static byte[] getBpmnBytes(BpmnModel model) {
            if (model == null) {
                return new byte[0];
            }
            BpmnXMLConverter converter = new BpmnXMLConverter();
            return converter.convertToXML(model);
        }

        // ========== Execution 相关 ==========

        public static String formatCollectionVariable(String activityId) {
            return activityId + "_assignees";
        }

        public static String formatCollectionElementVariable(String activityId) {
            return activityId + "_assignee";
        }

        public static <T> Map<String, List<T>> groupListContentBy(List<T> source, Function<T, String> classifier) {
            return source.stream().collect(Collectors.groupingBy(classifier));
        }

        // ======== 流程图遍历相关 =========

        public static Map<String, FlowNode> getCanReachTo(FlowNode toFlowNode) {
            return getCanReachTo(toFlowNode, null);
        }

        public static Map<String, FlowNode> getCanReachTo(FlowNode toFlowNode, Map<String, FlowNode> canReachToNodes) {
            if (canReachToNodes == null) {
                canReachToNodes = new HashMap<>(16);
            }
            List<SequenceFlow> flows = toFlowNode.getIncomingFlows();
            if (flows != null && !flows.isEmpty()) {
                for (SequenceFlow sequenceFlow : flows) {
                    FlowElement sourceFlowElement = sequenceFlow.getSourceFlowElement();
                    if (sourceFlowElement instanceof FlowNode) {
                        canReachToNodes.put(sourceFlowElement.getId(), (FlowNode) sourceFlowElement);
                        if (sourceFlowElement instanceof SubProcess) {
                            for (Map.Entry<String, FlowElement> entry : ((SubProcess) sourceFlowElement).getFlowElementMap().entrySet()) {
                                if (entry.getValue() instanceof FlowNode) {
                                    FlowNode flowNodeV = (FlowNode) entry.getValue();
                                    canReachToNodes.put(entry.getKey(), flowNodeV);
                                }
                            }
                        }
                        getCanReachTo((FlowNode) sourceFlowElement, canReachToNodes);
                    }
                }
            }
            if (toFlowNode.getSubProcess() != null) {
                getCanReachTo(toFlowNode.getSubProcess(), canReachToNodes);
            }
            return canReachToNodes;
        }

        public static Map<String, FlowNode> getCanReachFrom(FlowNode fromFlowNode) {
            return getCanReachFrom(fromFlowNode, null);
        }

        public static Map<String, FlowNode> getCanReachFrom(FlowNode fromFlowNode, Map<String, FlowNode> canReachFromNodes) {
            if (canReachFromNodes == null) {
                canReachFromNodes = new HashMap<>(16);
            }
            List<SequenceFlow> flows = fromFlowNode.getOutgoingFlows();
            if (flows != null && !flows.isEmpty()) {
                for (SequenceFlow sequenceFlow : flows) {
                    FlowElement targetFlowElement = sequenceFlow.getTargetFlowElement();
                    if (targetFlowElement instanceof FlowNode) {
                        canReachFromNodes.put(targetFlowElement.getId(), (FlowNode) targetFlowElement);
                        if (targetFlowElement instanceof SubProcess) {
                            for (Map.Entry<String, FlowElement> entry : ((SubProcess) targetFlowElement).getFlowElementMap().entrySet()) {
                                if (entry.getValue() instanceof FlowNode) {
                                    FlowNode flowNodeV = (FlowNode) entry.getValue();
                                    canReachFromNodes.put(entry.getKey(), flowNodeV);
                                }
                            }
                        }
                        getCanReachFrom((FlowNode) targetFlowElement, canReachFromNodes);
                    }
                }
            }
            if (fromFlowNode.getSubProcess() != null) {
                getCanReachFrom(fromFlowNode.getSubProcess(), canReachFromNodes);
            }
            return canReachFromNodes;
        }

        public static Map<String, Set<String>> getSpecialGatewayElements(FlowElementsContainer container) {
            return getSpecialGatewayElements(container, null);
        }

        public static Map<String, Set<String>> getSpecialGatewayElements(FlowElementsContainer container, Map<String, Set<String>> specialGatewayElements) {
            if (specialGatewayElements == null) {
                specialGatewayElements = new HashMap<>(16);
            }
            Collection<FlowElement> flowelements = container.getFlowElements();
            for (FlowElement flowElement : flowelements) {
                boolean isGateway = flowElement instanceof ParallelGateway || flowElement instanceof InclusiveGateway || flowElement instanceof ComplexGateway;
                if (isGateway) {
                    String gatewayId = flowElement.getId();
                    String endId = null;
                    if (gatewayId.endsWith(SPECIAL_GATEWAY_BEGIN_SUFFIX)) {
                        gatewayId = gatewayId.substring(0, gatewayId.length() - SPECIAL_GATEWAY_BEGIN_SUFFIX.length());
                        endId = gatewayId + SPECIAL_GATEWAY_END_SUFFIX;
                    } else {
                        FlowElement joinElement = null;
                        if (container instanceof Process) {
                            joinElement = ((Process) container).getFlowElement(gatewayId + SPECIAL_GATEWAY_JOIN_SUFFIX);
                            if (joinElement == null) {
                                joinElement = ((Process) container).getFlowElement(gatewayId + SPECIAL_GATEWAY_END_SUFFIX);
                            }
                        } else if (container instanceof SubProcess) {
                            joinElement = ((SubProcess) container).getFlowElement(gatewayId + SPECIAL_GATEWAY_JOIN_SUFFIX);
                            if (joinElement == null) {
                                joinElement = ((SubProcess) container).getFlowElement(gatewayId + SPECIAL_GATEWAY_END_SUFFIX);
                            }
                        }
                        if (joinElement != null) {
                            endId = joinElement.getId();
                        }
                    }
                    if (endId != null) {
                        Set<String> gatewayIdContainFlowelements = specialGatewayElements.computeIfAbsent(gatewayId, k -> new HashSet<>());
                        findElementsBetweenSpecialGateway(flowElement, endId, gatewayIdContainFlowelements);
                        continue;
                    }
                }
                if (flowElement instanceof SubProcess) {
                    getSpecialGatewayElements((SubProcess) flowElement, specialGatewayElements);
                }
            }

            // 按外层到里层排序
            Map<String, Set<String>> specialGatewayNodesSort = new LinkedHashMap<>();
            specialGatewayElements.entrySet().stream()
                    .sorted((o1, o2) -> o2.getValue().size() - o1.getValue().size())
                    .forEach(entry -> specialGatewayNodesSort.put(entry.getKey(), entry.getValue()));
            return specialGatewayNodesSort;
        }

        public static void findElementsBetweenSpecialGateway(FlowElement specialGatewayBegin, String specialGatewayEndId, Set<String> elements) {
            elements.add(specialGatewayBegin.getId());
            List<SequenceFlow> sequenceFlows = ((FlowNode) specialGatewayBegin).getOutgoingFlows();
            if (sequenceFlows != null && !sequenceFlows.isEmpty()) {
                for (SequenceFlow sequenceFlow : sequenceFlows) {
                    FlowElement targetFlowElement = sequenceFlow.getTargetFlowElement();
                    String targetFlowElementId = targetFlowElement.getId();
                    elements.add(specialGatewayEndId);
                    if (targetFlowElementId.equals(specialGatewayEndId)) {
                        continue;
                    } else {
                        findElementsBetweenSpecialGateway(targetFlowElement, specialGatewayEndId, elements);
                    }
                }
            }
        }

        /**
         * 验证 sourceElementId 是否可以到达 targetElementId
         */
        public static boolean isReachable(String processDefinitionId, String sourceElementId, String targetElementId) {
            Process process = ProcessDefinitionUtil.getProcess(processDefinitionId);
            FlowElement sourceFlowElement = process.getFlowElement(sourceElementId, true);
            FlowNode sourceElement = null;
            if (sourceFlowElement instanceof FlowNode) {
                sourceElement = (FlowNode) sourceFlowElement;
            } else if (sourceFlowElement instanceof SequenceFlow) {
                sourceElement = (FlowNode) ((SequenceFlow) sourceFlowElement).getTargetFlowElement();
            }
            FlowElement targetFlowElement = process.getFlowElement(targetElementId, true);
            FlowNode targetElement = null;
            if (targetFlowElement instanceof FlowNode) {
                targetElement = (FlowNode) targetFlowElement;
            } else if (targetFlowElement instanceof SequenceFlow) {
                targetElement = (FlowNode) ((SequenceFlow) targetFlowElement).getTargetFlowElement();
            }
            if (sourceElement == null) {
                throw new FlowableException("Invalid sourceElementId '" + sourceElementId + "': no element found for this id in process definition '" + processDefinitionId + "'");
            }
            if (targetElement == null) {
                throw new FlowableException("Invalid targetElementId '" + targetElementId + "': no element found for this id in process definition '" + processDefinitionId + "'");
            }
            Set<String> visitedElements = new HashSet<>();
            return isReachable(process, sourceElement, targetElement, visitedElements);
        }

        public static boolean isReachable(Process process, FlowNode sourceElement, FlowNode targetElement) {
            return isReachable(process, sourceElement, targetElement, new HashSet<>());
        }

        public static boolean isReachable(Process process, FlowNode sourceElement, FlowNode targetElement, Set<String> visitedElements) {
            if (sourceElement instanceof StartEvent && isInEventSubprocess(sourceElement)) {
                return false;
            }
            if (sourceElement.getOutgoingFlows().isEmpty()) {
                visitedElements.add(sourceElement.getId());
                FlowElementsContainer parentElement = process.findParent(sourceElement);
                if (parentElement instanceof SubProcess) {
                    sourceElement = (SubProcess) parentElement;
                    if (((SubProcess) sourceElement).getFlowElement(targetElement.getId()) != null) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            if (sourceElement.getId().equals(targetElement.getId())) {
                return true;
            }
            visitedElements.add(sourceElement.getId());
            if (sourceElement instanceof SubProcess && ((SubProcess) sourceElement).getFlowElement(targetElement.getId()) != null) {
                return true;
            }
            List<SequenceFlow> sequenceFlows = sourceElement.getOutgoingFlows();
            if (sequenceFlows != null && !sequenceFlows.isEmpty()) {
                for (SequenceFlow sequenceFlow : sequenceFlows) {
                    String targetRef = sequenceFlow.getTargetRef();
                    FlowNode sequenceFlowTarget = (FlowNode) process.getFlowElement(targetRef, true);
                    if (sequenceFlowTarget != null && !visitedElements.contains(sequenceFlowTarget.getId())) {
                        boolean reachable = isReachable(process, sequenceFlowTarget, targetElement, visitedElements);
                        if (reachable) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        protected static boolean isInEventSubprocess(FlowNode flowNode) {
            FlowElementsContainer flowElementsContainer = flowNode.getParentContainer();
            while (flowElementsContainer != null) {
                if (flowElementsContainer instanceof EventSubProcess) {
                    return true;
                }
                if (flowElementsContainer instanceof FlowElement) {
                    flowElementsContainer = ((FlowElement) flowElementsContainer).getParentContainer();
                } else {
                    flowElementsContainer = null;
                }
            }
            return false;
        }

        public static List<String> getParentProcessIds(FlowNode flowNode) {
            List<String> result = new ArrayList<>();
            FlowElementsContainer flowElementsContainer = flowNode.getParentContainer();
            while (flowElementsContainer != null) {
                if (flowElementsContainer instanceof SubProcess) {
                    SubProcess flowElement = (SubProcess) flowElementsContainer;
                    result.add(flowElement.getId());
                    flowElementsContainer = flowElement.getParentContainer();
                } else if (flowElementsContainer instanceof Process) {
                    Process flowElement = (Process) flowElementsContainer;
                    result.add(flowElement.getId());
                    flowElementsContainer = null;
                }
            }
            Collections.reverse(result);
            return result;
        }

        public static Integer getDiffLevel(List<String> sourceList, List<String> targetList) {
            if (sourceList == null || sourceList.isEmpty() || targetList == null || targetList.isEmpty()) {
                throw new FlowableException("sourceList and targetList cannot be empty");
            }
            if (sourceList.size() == 1 && targetList.size() == 1) {
                if (!sourceList.get(0).equals(targetList.get(0))) {
                    return 0;
                } else {
                    return -1;
                }
            }
            int minSize = Math.min(sourceList.size(), targetList.size());
            Integer targetLevel = null;
            for (int i = 0; i < minSize; i++) {
                if (!sourceList.get(i).equals(targetList.get(i))) {
                    targetLevel = i;
                    break;
                }
            }
            if (targetLevel == null) {
                if (sourceList.size() == targetList.size()) {
                    targetLevel = -1;
                } else {
                    targetLevel = minSize;
                }
            }
            return targetLevel;
        }

        public static Set<String> getParentExecutionIdsByActivityId(List<ExecutionEntity> executions, String activityId) {
            List<ExecutionEntity> activityIdExecutions = executions.stream()
                    .filter(e -> activityId.equals(e.getActivityId()))
                    .collect(Collectors.toList());
            if (activityIdExecutions.isEmpty()) {
                throw new FlowableException("Active execution could not be found with activity id " + activityId);
            }
            ExecutionEntity miExecution = null;
            boolean isInsideMultiInstance = false;
            for (ExecutionEntity possibleMiExecution : activityIdExecutions) {
                if (possibleMiExecution.isMultiInstanceRoot()) {
                    miExecution = possibleMiExecution;
                    isInsideMultiInstance = true;
                    break;
                }
                if (isExecutionInsideMultiInstance(possibleMiExecution)) {
                    isInsideMultiInstance = true;
                }
            }
            Set<String> parentExecutionIds = new HashSet<>();
            if (isInsideMultiInstance) {
                Stream<ExecutionEntity> executionEntitiesStream = activityIdExecutions.stream();
                if (miExecution != null) {
                    executionEntitiesStream = executionEntitiesStream.filter(ExecutionEntity::isMultiInstanceRoot);
                }
                executionEntitiesStream.forEach(childExecution -> parentExecutionIds.add(childExecution.getParentId()));
            } else {
                ExecutionEntity execution = activityIdExecutions.iterator().next();
                parentExecutionIds.add(execution.getParentId());
            }
            return parentExecutionIds;
        }

        public static boolean isExecutionInsideMultiInstance(ExecutionEntity execution) {
            return getFlowElementMultiInstanceParentId(execution.getCurrentFlowElement()).isPresent();
        }

        public static Optional<String> getFlowElementMultiInstanceParentId(FlowElement flowElement) {
            FlowElementsContainer parentContainer = flowElement.getParentContainer();
            while (parentContainer instanceof Activity) {
                if (isFlowElementMultiInstance((Activity) parentContainer)) {
                    return Optional.of(((Activity) parentContainer).getId());
                }
                parentContainer = ((Activity) parentContainer).getParentContainer();
            }
            return Optional.empty();
        }

        public static boolean isFlowElementMultiInstance(FlowElement flowElement) {
            if (flowElement instanceof Activity) {
                return ((Activity) flowElement).getLoopCharacteristics() != null;
            }
            return false;
        }

        public static String getParentExecutionIdFromParentIds(ExecutionEntity execution, Set<String> parentExecutionIds) {
            ExecutionEntity taskParentExecution = execution.getParent();
            String realParentExecutionId = null;
            while (taskParentExecution != null) {
                if (parentExecutionIds.contains(taskParentExecution.getId())) {
                    realParentExecutionId = taskParentExecution.getId();
                    break;
                }
                taskParentExecution = taskParentExecution.getParent();
            }
            if (realParentExecutionId == null || realParentExecutionId.length() == 0) {
                throw new FlowableException("Parent execution could not be found with executionId id " + execution.getId());
            }
            return realParentExecutionId;
        }

        public static String[] getSourceAndTargetRealActivityId(FlowNode sourceFlowElement, FlowNode targetFlowElement) {
            String sourceRealActivityId = sourceFlowElement.getId();
            String targetRealActivityId = targetFlowElement.getId();
            List<String> sourceParentProcesss = getParentProcessIds(sourceFlowElement);
            List<String> targetParentProcesss = getParentProcessIds(targetFlowElement);
            int diffParentLevel = getDiffLevel(sourceParentProcesss, targetParentProcesss);
            if (diffParentLevel != -1) {
                sourceRealActivityId = sourceParentProcesss.size() == diffParentLevel ? sourceRealActivityId : sourceParentProcesss.get(diffParentLevel);
                targetRealActivityId = targetParentProcesss.size() == diffParentLevel ? targetRealActivityId : targetParentProcesss.get(diffParentLevel);
            }
            return new String[]{sourceRealActivityId, targetRealActivityId};
        }

        public static String getAttributeValue(BaseElement element, String namespace, String name) {
            return element.getAttributeValue(namespace, name);
        }

        public static String getFlowableAttributeValue(BaseElement element, String name) {
            return element.getAttributeValue(FLOWABLE_NAMESPACE, name);
        }

        public static List<ExtensionElement> getExtensionElements(BaseElement element, String name) {
            return element.getExtensionElements().get(name);
        }

        public static FlowElement getFlowElement(RepositoryService repositoryService, String processDefinitionId, String flowElementId, boolean searchRecursive) {
            Process process = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
            return process.getFlowElement(flowElementId, searchRecursive);
        }

        public static FlowElement getFlowElement(RepositoryService repositoryService, String processDefinitionId, String flowElementId) {
            return getFlowElement(repositoryService, processDefinitionId, flowElementId, true);
        }
}
