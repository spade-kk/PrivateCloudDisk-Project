package org.project.privateclouddiskgatewayservice.config.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nacos 响应式服务发现客户端
 * <p>
 * 实现 Spring Cloud 的 {@link ReactiveDiscoveryClient} 接口，
 * 使 Spring Cloud LoadBalancer 可以通过 {@code lb://service-name} 自动发现服务实例。
 * <p>
 * 由于 Nacos 客户端是阻塞式 API，通过 {@link Schedulers#boundedElastic()} 调度到弹性线程池。
 */
@Slf4j
public class NacosReactiveDiscoveryClient implements ReactiveDiscoveryClient {

    private final NamingService namingService;
    private final String group;

    public NacosReactiveDiscoveryClient(NamingService namingService, String group) {
        this.namingService = namingService;
        this.group = group;
    }

    @Override
    public String description() {
        return "Nacos Reactive Discovery Client";
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Mono.fromCallable(() -> {
                    List<Instance> instances = namingService.selectInstances(serviceId, group, true);
                    return instances.stream()
                            .map(this::toServiceInstance)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .doOnError(e -> log.error("Nacos 获取服务实例失败: serviceId={}", serviceId, e))
                .onErrorResume(e -> Flux.empty());
    }

    @Override
    public Flux<String> getServices() {
        return Mono.fromCallable(() -> namingService.getServicesOfServer(1, Integer.MAX_VALUE, group).getData())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .doOnError(e -> log.error("Nacos 获取服务列表失败", e))
                .onErrorResume(e -> Flux.empty());
    }

    /**
     * 将 Nacos Instance 转换为 Spring Cloud ServiceInstance
     */
    private ServiceInstance toServiceInstance(Instance instance) {
        return new NacosServiceInstance(instance);
    }

    /**
     * Nacos 服务实例适配器
     */
    record NacosServiceInstance(Instance instance) implements ServiceInstance {

        @Override
        public String getServiceId() {
            return instance.getServiceName();
        }

        @Override
        public String getHost() {
            return instance.getIp();
        }

        @Override
        public int getPort() {
            return instance.getPort();
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public URI getUri() {
            return URI.create(String.format("http://%s:%d", instance.getIp(), instance.getPort()));
        }

        @Override
        public Map<String, String> getMetadata() {
            Map<String, String> metadata = new HashMap<>(instance.getMetadata());
            metadata.put("weight", String.valueOf(instance.getWeight()));
            metadata.put("healthy", String.valueOf(instance.isHealthy()));
            metadata.put("cluster", instance.getClusterName());
            return metadata;
        }

        @Override
        public String getInstanceId() {
            return instance.getInstanceId();
        }
    }
}