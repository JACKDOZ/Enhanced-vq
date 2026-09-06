package com.homequest.block;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Geometría 3D propia del cofre de misiones — reemplaza la reutilización del
 * ChestModel de vanilla con la forma exacta que rehizo el usuario en
 * Blockbench (chest.bbmodel), en vez de solo re-texturizar la forma vanilla
 * (eso fue lo que dio muchos bugs de alineación antes).
 *
 * Los 3 cubos (base/lid/knob) y sus posiciones son los mismos "from"/"to" del
 * .bbmodel, tal cual — no se inventó ninguna medida. Los child parts se
 * llaman "bottom", "lid" y "lock" porque así los busca por nombre el
 * ChestModel de vanilla internamente (se lo sigue usando para el renderer y
 * la animación de abrir/cerrar — ver QuestChestRenderer/HomeQuestClient),
 * solo con ESTA geometría en vez de la vanilla.
 *
 * La textura (textures/entity/chest/quest_chest.png) es un box-UV estándar
 * de 56x48 generado a partir de los UV explícitos por cara del .bbmodel
 * original (que usaba un atlas de 1024x1024 no estándar) — ver el layout de
 * offsets abajo, que tiene que coincidir exacto con cómo se armó esa imagen.
 */
public final class QuestChestModel {
    private QuestChestModel() {}

    public static final int TEX_W = 56, TEX_H = 48;

    // Geometría y UV sacadas del archivo OBJ que mandó el usuario (fuente de
    // verdad más precisa que el .bbmodel: da coordenadas UV explícitas por
    // vértice, sin tener que adivinar ningún factor de escala). Offsets de
    // textura: base en (0,0), lid en (0,24), knob en (0,43) — mismo layout que
    // se usó para armar quest_chest.png a partir de esos UV exactos.
    public static LayerDefinition createSingleBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // "bottom" — sin pivote especial. En el OBJ: from(-7,0,-7) a (7,10,7).
        root.addOrReplaceChild("bottom",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7f, 0f, -7f, 14f, 10f, 14f),
            PartPose.ZERO);

        // "lid" — pivote en el borde de atrás (sur, Z positivo — el lado
        // opuesto al pestillo, que en el OBJ queda del lado norte/Z negativo),
        // para que la animación de abrir/cerrar gire desde la bisagra real.
        // El cubo cuelga POR DEBAJO del pivote (origen Y negativo) — eso ya
        // estaba bien (fue el fix correcto para que abriera hacia arriba y no
        // mostrara la cara de "adentro" hacia afuera).
        //
        // BUG RAÍZ #2 (2026-09-03): el pivote Y estaba en 9, un valor que
        // solo tenía sentido con el cubo colgando por ARRIBA (versión vieja).
        // Al invertir el cubo para que cuelgue por ABAJO sin subir el pivote
        // a la par, la tapa quedó en Y mundo 4..9 — completamente HUNDIDA
        // dentro de "bottom" (que ocupa Y 0..10) en vez de apoyada encima.
        // Esas 5 unidades de volumen compartido con la misma huella X/Z que
        // "bottom" son la causa de las franjas verticales inconsistentes y
        // del z-fighting horizontal. Con pivote en 14, la tapa queda en Y
        // mundo 9..14: apoyada sobre "bottom" con el mismo único unit de
        // solape en la bisagra que usa el cofre vanilla (ahí sí es intencional
        // y no da flicker, porque ya no hay caras completas coincidiendo).
        root.addOrReplaceChild("lid",
            CubeListBuilder.create().texOffs(0, 24)
                .addBox(-7f, -5f, -14f, 14f, 5f, 14f),
            PartPose.offset(0f, 14f, 7f));

        // "lock" — el pestillo. En el OBJ: from(-1,7,-8) a (1,11,-7).
        //
        // BUG (2026-09-04, tercera vuelta): ChestModel.setupAnim de vanilla hace SIEMPRE
        // lock.xRot = lid.xRot (asume que el pestillo esta clavado a la tapa y gira con
        // ella). Con PartPose.ZERO ese giro pasaba a ocurrir alrededor del ORIGEN DEL MUNDO
        // (0,0,0) — un punto lejos del pestillo — asi que al abrir la tapa el pestillo
        // salia volando en un arco grande en vez de quedarse quieto ("la chapa pasa a
        // estar flotando en la parte posterior"). Cerrado (xRot=0) no se notaba porque
        // cualquier pivote da la misma posicion de reposo.
        // No podemos evitar que vanilla copie el angulo (no editamos ChestModel), asi que
        // el arreglo disponible aca es darle un pivote en su propio centro: seguira
        // inclinandose un poco junto con la tapa, pero ya no se desplaza lejos de donde
        // deberia estar. Solucion definitiva de verdad: Opcion B, donde el renderer arma
        // la rotacion a mano y el pestillo se puede dejar fijo de verdad.
        root.addOrReplaceChild("lock",
            CubeListBuilder.create().texOffs(0, 43)
                .addBox(-1f, -2f, -0.5f, 2f, 4f, 1f),
            PartPose.offset(0f, 9f, -7.5f));

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }
}
