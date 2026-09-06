package com.homequest.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Renderer del Cofre de Quest — cofre 100% real y funcional (misma base que se validó en
 * el mod de prueba chesttest): usa el ChestModel real de Minecraft (misma animación de tapa
 * que un cofre vanilla) con nuestra propia textura independiente
 * (textures/entity/chest/quest_chest.png), sin tocar ningún recurso vanilla compartido.
 */
public class QuestChestRenderer implements BlockEntityRenderer<QuestChestBlockEntity, QuestChestRenderState> {

    public static final ModelLayerLocation LAYER_SINGLE =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath("homequest", "quest_chest"), "single");
    public static final ModelLayerLocation LAYER_LEFT =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath("homequest", "quest_chest"), "left");
    public static final ModelLayerLocation LAYER_RIGHT =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath("homequest", "quest_chest"), "right");

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("homequest", "textures/entity/chest/quest_chest.png");

    private final ChestModel singleModel;
    private final ChestModel leftModel;
    private final ChestModel rightModel;

    public QuestChestRenderer(BlockEntityRendererProvider.Context context) {
        this.singleModel = new ChestModel(context.bakeLayer(LAYER_SINGLE));
        this.leftModel = new ChestModel(context.bakeLayer(LAYER_LEFT));
        this.rightModel = new ChestModel(context.bakeLayer(LAYER_RIGHT));
    }

    @Override
    public QuestChestRenderState createRenderState() {
        return new QuestChestRenderState();
    }

    @Override
    public void extractRenderState(QuestChestBlockEntity entity, QuestChestRenderState state, float tickProgress,
                                    Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(entity, state, crumblingOverlay);
        BlockState blockState = entity.getBlockState();
        state.facing = blockState.getValue(ChestBlock.FACING);
        state.type = blockState.getValue(ChestBlock.TYPE);
        state.open = entity.getOpenNess(tickProgress);
    }

    @Override
    public void submit(QuestChestRenderState state, PoseStack pose, SubmitNodeCollector collector,
                        CameraRenderState camera) {
        ChestModel model = switch (state.type) {
            case LEFT -> leftModel;
            case RIGHT -> rightModel;
            default -> singleModel;
        };

        pose.pushPose();
        // BUG (encontrado 2026-09-04): rotationAround(q, 0.5,0,0.5) SOLO gira alrededor de
        // ese punto — no traslada ahi la geometria. Nuestro modelo esta centrado en su propio
        // (0,0,0) local (X y Z rondan -0.44..0.44), asi que sin esta traslacion quedaba
        // dibujado centrado en la ESQUINA del bloque (0,0,0) en vez de en su CENTRO (0.5,0,0.5)
        // — de ahi el cofre "corrido" respecto al hitbox real (que Minecraft calcula aparte y
        // por eso se veia bien plantado mientras el modelo quedaba a un lado). Y no sirve
        // arreglarlo con SOLO rotationAround por mas pivote que se le de: hay que trasladar
        // primero y despues rotar, en dos pasos.
        pose.translate(0.5, 0.0, 0.5);
        // BUG (2026-09-04, segunda vuelta): con el offset de arriba ya arreglado, quedaba un
        // giro de 180° de mas — la hebilla terminaba mirando hacia el lado CONTRARIO al que
        // indica el facing del bloque (para verla de frente había que rodear el cofre). El
        // modelo tiene su propio "frente" (la cerradura) apuntando a -Z local; la formula de
        // vanilla para bloques con FACING asume la convencion opuesta, asi que falta este
        // +180 constante — no es un tema de signo por direccion, es un desfase fijo.
        pose.mulPose(Axis.YP.rotationDegrees(180f - state.facing.toYRot()));

        // misma curva de easing que usa vanilla para la animacion de la tapa: f = 1-(1-open)^3
        float f = state.open;
        f = 1.0f - f;
        f = 1.0f - f * f * f;

        // ChestModel.setupAnim (vanilla) hace SIEMPRE lid.xRot = -f * 90°. Esa formula esta
        // calibrada para un cubo de tapa que se extiende en +Z desde la bisagra (asi es como
        // vanilla arma su propio ChestModel). El nuestro se extiende en -Z desde la bisagra
        // (coherente con que la palanca/pestillo queda del lado -Z, ver QuestChestModel.java) —
        // es el espejo del original. Con el signo de vanilla sin cambiar, la tapa gira hacia
        // abajo y a un lado en vez de subir hacia atras (bug reportado: "la tapa esta al reves,
        // se abre hacia atras"). Como no podemos tocar ChestModel, se compensa invirtiendo el
        // valor que le pasamos: -f produce +f*90° adentro de vanilla, que es la que sube
        // correctamente la tapa hacia atras sobre la bisagra. Verificado con la matriz de
        // rotacion real (Quaternionf.rotationZYX) que usa ModelPart, no a ojo.
        float lidAnim = -f;

        RenderType renderType = RenderTypes.entitySolid(TEXTURE);
        collector.order(0).submitModel(model, lidAnim, pose, renderType,
            state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);

        pose.popPose();
    }
}
