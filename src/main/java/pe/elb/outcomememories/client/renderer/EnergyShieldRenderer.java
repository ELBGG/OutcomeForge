package pe.elb.outcomememories.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import pe.elb.outcomememories.Outcomememories;

public class EnergyShieldRenderer {

    private static final ResourceLocation SHIELD_TEXTURE = 
        new ResourceLocation(Outcomememories.MODID, "textures/misc/white.png");


    public static void renderEnergyShield(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         int packedLight, int packedOverlay, float warningPhase) {
        poseStack.pushPose();

        long timeMs = System.currentTimeMillis();
        float animTime = (timeMs % 2000L) / 2000.0F;

        float rotation = animTime * 360.0F;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));

        float pulseFactor = 1.0F + (float)Math.sin(animTime * Math.PI * 4) * 0.05F;
        float scale = 1.8F * pulseFactor;
        poseStack.scale(scale, scale, scale);
        
        renderShieldSphere(poseStack, bufferSource, packedOverlay, animTime, warningPhase);
        
        poseStack.popPose();
    }
    
    private static void renderShieldSphere(PoseStack poseStack, MultiBufferSource bufferSource,
                                           int packedOverlay, float animTime, float warningPhase) {
        float radius = 0.6F; // Radio de la esfera
        int longitudeSteps = 32; // Más detallado que singularity
        int latitudeSteps = 24;
        
        // ===== COLORES DEL ESCUDO ELÉCTRICO =====
        
        // Color base: Azul eléctrico brillante
        float baseR = 0.2F;
        float baseG = 0.5F;
        float baseB = 1.0F;
        
        // Color de advertencia: Amarillo/Naranja
        float warnR = 1.0F;
        float warnG = 0.8F;
        float warnB = 0.0F;
        
        // Interpolación entre normal y advertencia
        float r = lerp(baseR, warnR, warningPhase);
        float g = lerp(baseG, warnG, warningPhase);
        float b = lerp(baseB, warnB, warningPhase);
        
        // Pulso de brillo
        float brightnessPulse = 0.5F + (float)Math.sin(animTime * Math.PI * 6) * 0.3F;
        r = Math.min(1.0F, r * (1.0F + brightnessPulse));
        g = Math.min(1.0F, g * (1.0F + brightnessPulse));
        b = Math.min(1.0F, b * (1.0F + brightnessPulse));
        
        // Transparencia - flash blanco cuando está en advertencia
        float alpha = 0.4F;
        if (warningPhase > 0.5F) {
            // Flash rápido
            float flashSpeed = (System.currentTimeMillis() % 200L) / 200.0F;
            alpha = lerp(0.4F, 0.9F, (float)Math.sin(flashSpeed * Math.PI * 2));
        }
        
        // Render type translúcido con emisividad
        var renderType = RenderType.entityTranslucentEmissive(SHIELD_TEXTURE);
        var vertexConsumer = bufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        
        // Renderizar esfera con patrón hexagonal (efecto de panel de energía)
        for (int i = 0; i < latitudeSteps; i++) {
            float lat0 = (float) Math.PI * (-0.5F + (float) i / latitudeSteps);
            float lat1 = (float) Math.PI * (-0.5F + (float) (i + 1) / latitudeSteps);
            float z0 = (float) Math.sin(lat0) * radius;
            float zr0 = (float) Math.cos(lat0) * radius;
            float z1 = (float) Math.sin(lat1) * radius;
            float zr1 = (float) Math.cos(lat1) * radius;
            
            for (int j = 0; j < longitudeSteps; j++) {
                int jp1 = (j + 1) % longitudeSteps;
                float lng0 = 2.0F * (float) Math.PI * j / longitudeSteps;
                float lng1 = 2.0F * (float) Math.PI * jp1 / longitudeSteps;
                float x0 = (float) Math.cos(lng0);
                float y0 = (float) Math.sin(lng0);
                float x1 = (float) Math.cos(lng1);
                float y1 = (float) Math.sin(lng1);
                
                // UVs animadas para efecto de energía fluyendo
                float uvOffset = animTime * 0.5F;
                float u0 = ((float) j / longitudeSteps) + uvOffset;
                float u1 = ((float) jp1 / longitudeSteps) + uvOffset;
                float v0 = (float) i / latitudeSteps;
                float v1 = (float) (i + 1) / latitudeSteps;
                
                // Variación de brillo por panel (efecto hexagonal)
                float panelBrightness = 1.0F;
                if ((i + j) % 2 == 0) {
                    panelBrightness = 0.8F; // Paneles alternados más oscuros
                }
                
                float finalR = r * panelBrightness;
                float finalG = g * panelBrightness;
                float finalB = b * panelBrightness;
                
                // Quad del escudo
                vertexConsumer.vertex(matrix, zr0 * x0, z0, zr0 * y0)
                        .color(finalR, finalG, finalB, alpha)
                        .uv(u0, v0)
                        .overlayCoords(packedOverlay)
                        .uv2(240) // Luz máxima para efecto emisivo
                        .normal(normalMatrix, x0, 0, y0)
                        .endVertex();
                vertexConsumer.vertex(matrix, zr1 * x0, z1, zr1 * y0)
                        .color(finalR, finalG, finalB, alpha)
                        .uv(u0, v1)
                        .overlayCoords(packedOverlay)
                        .uv2(240)
                        .normal(normalMatrix, x0, 0, y0)
                        .endVertex();
                vertexConsumer.vertex(matrix, zr1 * x1, z1, zr1 * y1)
                        .color(finalR, finalG, finalB, alpha)
                        .uv(u1, v1)
                        .overlayCoords(packedOverlay)
                        .uv2(240)
                        .normal(normalMatrix, x1, 0, y1)
                        .endVertex();
                vertexConsumer.vertex(matrix, zr0 * x1, z0, zr0 * y1)
                        .color(finalR, finalG, finalB, alpha)
                        .uv(u1, v0)
                        .overlayCoords(packedOverlay)
                        .uv2(240)
                        .normal(normalMatrix, x1, 0, y1)
                        .endVertex();
            }
        }
    }
    
    /**
     * Renderiza líneas de energía eléctrica (chispas) alrededor del escudo
     */
    public static void renderElectricArcs(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         int packedLight, int packedOverlay) {
        poseStack.pushPose();
        
        long timeMs = System.currentTimeMillis();
        int numArcs = 8; // Número de arcos eléctricos
        
        var renderType = RenderType.lightning();
        var vertexConsumer = bufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        
        for (int i = 0; i < numArcs; i++) {
            // Posición aleatoria pero determinista basada en tiempo
            long seed = timeMs / 100 + i * 1000;
            float angle = (seed % 360) * (float)Math.PI / 180.0F;
            float height = ((seed % 100) / 100.0F - 0.5F) * 2.0F;
            
            float x = (float)Math.cos(angle) * 0.8F;
            float y = height;
            float z = (float)Math.sin(angle) * 0.8F;
            
            // Color eléctrico brillante
            float r = 0.5F + ((seed % 50) / 100.0F);
            float g = 0.7F + ((seed % 30) / 100.0F);
            float b = 1.0F;
            float a = 0.6F;
            
            // Línea de chispa (simplificada)
            vertexConsumer.vertex(matrix, x, y, z)
                    .color(r, g, b, a)
                    .endVertex();
            vertexConsumer.vertex(matrix, x * 0.6F, y * 0.8F, z * 0.6F)
                    .color(r, g, b, 0.0F)
                    .endVertex();
        }
        
        poseStack.popPose();
    }
    
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}