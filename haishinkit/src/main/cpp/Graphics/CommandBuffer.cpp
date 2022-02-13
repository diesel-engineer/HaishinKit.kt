#include "../haishinkit.hpp"
#include "Kernel.h"
#include "Util.h"
#include "CommandBuffer.h"
#include "PushConstants.hpp"

using namespace Graphics;

const Vertex CommandBuffer::VERTICES[] = {
        {{-1.f, 1.f},  {0.f, 1.f}},
        {{1.f,  1.f},  {1.f, 1.f}},
        {{-1.f, -1.f}, {0.f, 0.f}},
        {{1.f,  -1.f}, {1.f, 0.f}},
};

CommandBuffer::CommandBuffer() = default;

CommandBuffer::~CommandBuffer() = default;

void CommandBuffer::SetUp(Kernel &kernel) {
    commandPool = kernel.device->createCommandPoolUnique(
            vk::CommandPoolCreateInfo()
                    .setPNext(nullptr)
                    .setFlags(
                            vk::CommandPoolCreateFlags(
                                    vk::CommandPoolCreateFlagBits::eResetCommandBuffer))
                    .setQueueFamilyIndex(kernel.queue.queueFamilyIndex)
    );

    auto imagesCount = kernel.swapChain.GetImagesCount();
    commandBuffers = kernel.device->allocateCommandBuffersUnique(
            vk::CommandBufferAllocateInfo()
                    .setPNext(nullptr)
                    .setCommandPool(commandPool.get())
                    .setLevel(vk::CommandBufferLevel::ePrimary)
                    .setCommandBufferCount(imagesCount)
    );

    framebuffers.resize(imagesCount);
    for (auto i = 0; i < imagesCount; ++i) {
        framebuffers[i] = kernel.swapChain.CreateFramebuffer(kernel, i);
    }

    buffers.resize(1);
    buffers[0] = CreateBuffer(kernel, (void *) CommandBuffer::VERTICES,
                              sizeof(CommandBuffer::VERTICES) * sizeof(Graphics::Vertex));

    offsets.resize(1);
    offsets[0] = {0};
}

void CommandBuffer::TearDown(Kernel &kernel) {
    commandBuffers.clear();
    commandBuffers.shrink_to_fit();
    for (auto &buffer : buffers) {
        kernel.device->destroy(buffer);
    }
    for (auto &framebuffer : framebuffers) {
        kernel.device->destroy(framebuffer);
    }
}

void CommandBuffer::SetTextures(Kernel &kernel, const std::vector<Texture *> &textures) {
    const auto colors = {vk::ClearValue().setColor(
            vk::ClearColorValue().setFloat32({0.f, 0.f, 0.f, 1.f}))};

    const std::vector<vk::Rect2D> scissors = {
            vk::Rect2D().setExtent(kernel.swapChain.size).setOffset({0, 0})
    };

    const std::vector<vk::Viewport> viewports = {
            textures[0]->GetViewport(kernel)
    };

    const PushConstants pushConstantsBlock =
            textures[0]->GetPushConstants(kernel);

    for (auto i = 0; i < commandBuffers.size(); ++i) {
        auto &commandBuffer = commandBuffers[i].get();
        commandBuffer.begin(
                vk::CommandBufferBeginInfo()
                        .setFlags(vk::CommandBufferUsageFlagBits::eRenderPassContinue));

        commandBuffer.setViewport(0, viewports);
        commandBuffer.setScissor(0, scissors);

        commandBuffer.pushConstants(
                kernel.pipeline.pipelineLayout.get(),
                vk::ShaderStageFlagBits::eVertex,
                0,
                sizeof(PushConstants),
                &pushConstantsBlock);

        commandBuffer.beginRenderPass(
                vk::RenderPassBeginInfo()
                        .setRenderPass(kernel.swapChain.renderPass.get())
                        .setFramebuffer(framebuffers[i])
                        .setRenderArea(
                                vk::Rect2D().setOffset({0, 0}).setExtent(kernel.swapChain.size))
                        .setClearValues(colors),
                vk::SubpassContents::eInline);

        commandBuffer.bindPipeline(vk::PipelineBindPoint::eGraphics,
                                   kernel.pipeline.pipeline.get());

        commandBuffer.bindVertexBuffers(0, buffers, offsets);
        commandBuffer.bindDescriptorSets(
                vk::PipelineBindPoint::eGraphics,
                kernel.pipeline.pipelineLayout.get(),
                0,
                1,
                &kernel.pipeline.descriptorSets[0].get(),
                0,
                nullptr);

        commandBuffer.draw(4, 1, 0, 0);
        commandBuffer.endRenderPass();
        commandBuffer.end();
    }

    for (const auto &texture : textures) {
        texture->invalidateLayout = false;
    }
    kernel.invalidateSurfaceRotation = false;
}

vk::CommandBuffer CommandBuffer::Allocate(Kernel &kernel) {
    const auto commandBuffers = kernel.device->allocateCommandBuffers(
            vk::CommandBufferAllocateInfo()
                    .setCommandBufferCount(1)
                    .setCommandPool(commandPool.get())
                    .setLevel(vk::CommandBufferLevel::ePrimary)
    );
    return commandBuffers[0];
}

vk::Buffer CommandBuffer::CreateBuffer(Kernel &kernel, void *data, vk::DeviceSize size) {
    const auto result = kernel.device->createBuffer(
            vk::BufferCreateInfo()
                    .setSize(size)
                    .setUsage(vk::BufferUsageFlagBits::eVertexBuffer)
                    .setQueueFamilyIndexCount(1)
                    .setSharingMode(vk::SharingMode::eExclusive)
                    .setQueueFamilyIndices(kernel.queue.queueFamilyIndex)
    );
    const auto memoryRequirements = kernel.device->getBufferMemoryRequirements(
            result);
    const auto memory = kernel.device->allocateMemory(
            vk::MemoryAllocateInfo()
                    .setAllocationSize(
                            memoryRequirements.size)
                    .setMemoryTypeIndex(
                            kernel.FindMemoryType(memoryRequirements.memoryTypeBits,
                                                  vk::MemoryPropertyFlagBits::eHostVisible |
                                                  vk::MemoryPropertyFlagBits::eHostCoherent))
    );
    void *map = kernel.device->mapMemory(memory, 0, memoryRequirements.size);
    memcpy(map, data, size);
    kernel.device->unmapMemory(memory);
    kernel.device->bindBufferMemory(result, memory, 0);
    return result;
}
