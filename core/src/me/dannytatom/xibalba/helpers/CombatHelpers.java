package me.dannytatom.xibalba.helpers;

import aurelienribon.tweenengine.Tween;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.Objects;

import me.dannytatom.xibalba.Main;
import me.dannytatom.xibalba.abilities.Ability;
import me.dannytatom.xibalba.components.AttributesComponent;
import me.dannytatom.xibalba.components.BodyComponent;
import me.dannytatom.xibalba.components.BrainComponent;
import me.dannytatom.xibalba.components.EffectsComponent;
import me.dannytatom.xibalba.components.GodComponent;
import me.dannytatom.xibalba.components.ItemComponent;
import me.dannytatom.xibalba.components.PlayerComponent;
import me.dannytatom.xibalba.components.SkillsComponent;
import me.dannytatom.xibalba.components.VisualComponent;
import me.dannytatom.xibalba.components.actions.MeleeComponent;
import me.dannytatom.xibalba.components.actions.RangeComponent;
import me.dannytatom.xibalba.components.items.WeaponComponent;
import me.dannytatom.xibalba.components.statuses.BleedingComponent;
import me.dannytatom.xibalba.components.statuses.CrippledComponent;
import me.dannytatom.xibalba.effects.Effect;
import me.dannytatom.xibalba.utils.ComponentMappers;
import me.dannytatom.xibalba.utils.SpriteAccessor;
import me.dannytatom.xibalba.world.WorldManager;

/**
 * t
 */
public class CombatHelpers {
  public CombatHelpers() {

  }

  /**
   * Add MeleeComponent to player.
   *
   * @param enemy    Who ya hitting
   * @param bodyPart Where ya hitting them at
   */
  public void preparePlayerForMelee(Entity enemy, String bodyPart, boolean isFocused) {
    AttributesComponent attributes = ComponentMappers.attributes.get(WorldManager.player);

    if (attributes.energy >= MeleeComponent.COST) {
      WorldManager.player.add(new MeleeComponent(enemy, bodyPart, isFocused));
    }
  }

  /**
   * Add RangeComponent to player for throwing.
   *
   * @param position Where ya throwing
   * @param bodyPart Where you trying to hit em
   */
  public void preparePlayerForThrowing(Vector2 position, String bodyPart, boolean isFocused) {
    AttributesComponent attributes = ComponentMappers.attributes.get(WorldManager.player);

    if (attributes.energy >= RangeComponent.COST) {
      Entity item = WorldManager.itemHelpers.getThrowing(WorldManager.player);

      WorldManager.player.add(new RangeComponent(position, item, "throwing", bodyPart, isFocused));
    }
  }

  /**
   * Add RangeComponent to player for range weapons.
   *
   * @param position Where ya shooting
   * @param bodyPart Where you trying to hit em
   */
  public void preparePlayerForRanged(Vector2 position, String bodyPart, boolean isFocused) {
    AttributesComponent attributes = ComponentMappers.attributes.get(WorldManager.player);

    if (attributes.energy >= RangeComponent.COST) {
      Entity primaryWeapon = WorldManager.itemHelpers.getRightHand(WorldManager.player);
      WeaponComponent weapon = ComponentMappers.weapon.get(primaryWeapon);

      Entity item = WorldManager.itemHelpers.getAmmunitionOfType(
          WorldManager.player, weapon.ammunitionType
      );

      ItemComponent itemDetails = ComponentMappers.item.get(item);

      WorldManager.player.add(
          new RangeComponent(position, item, itemDetails.skill, bodyPart, isFocused)
      );
    }
  }

  /**
   * Handles melee combat.
   *
   * @param starter  Who started the fight
   * @param target   Who they fightin'
   * @param bodyPart Where they hittin'
   */
  public void melee(Entity starter, Entity target, String bodyPart, boolean isFocused) {
    Entity item = null;

    if (ComponentMappers.equipment.has(starter)) {
      item = WorldManager.itemHelpers.getRightHand(starter);
    }

    String skill = item == null ? "unarmed" : ComponentMappers.item.get(item).skill;

    SkillsComponent starterSkills = ComponentMappers.skills.get(starter);
    int skillLevel = starterSkills.levels.get(skill);

    int hit = rollHit(starter, target, skillLevel, bodyPart);

    if (hit > 0) {
      if (ComponentMappers.player.has(starter)) {
        PlayerComponent playerDetails = ComponentMappers.player.get(starter);

        playerDetails.lastHitEntity = target;
        playerDetails.totalHits += 1;
      }

      int damage = rollDamage(AttackType.MELEE, starter, target, item, hit, bodyPart);

      applyDamage(starter, target, item, damage, skill, bodyPart, isFocused);
    } else {
      if (ComponentMappers.player.has(starter)) {
        ComponentMappers.player.get(starter).totalMisses += 1;
      }
    }
  }

  /**
   * Handles range combat.
   *
   * @param starter  Who started the fight
   * @param target   Who they fightin'
   * @param bodyPart Where they hittin'
   * @param item     What item they're hitting them w/
   * @param skill    What skill to use (throw if throwing, archery if bow, etc0
   */
  public void range(Entity starter, Entity target, String bodyPart,
                    Entity item, String skill, boolean isFocused) {
    SkillsComponent starterSkills = ComponentMappers.skills.get(starter);
    int skillLevel = starterSkills.levels.get(skill);

    int hit = rollHit(starter, target, skillLevel, bodyPart);

    if (hit > 0) {
      if (ComponentMappers.player.has(starter)) {
        PlayerComponent playerDetails = ComponentMappers.player.get(starter);

        playerDetails.lastHitEntity = target;
        playerDetails.totalHits += 1;
      }

      int damage = rollDamage(Objects.equals(skill, "throwing")
          ? AttackType.THROW
          : AttackType.RANGE, starter, target, item, hit, bodyPart);

      applyDamage(starter, target, item, damage, skill, bodyPart, isFocused);
    } else {
      if (ComponentMappers.player.has(starter)) {
        ComponentMappers.player.get(starter).totalMisses += 1;
      }

      WorldManager.log.add("combat.missed", getName(starter), getName(target));
    }
  }

  private int rollHit(Entity starter, Entity target, int skillLevel, String bodyPart) {
    // Roll relevant skill and a 6, highest result is used as your hit roll

    int skillRoll = skillLevel == 0 ? 0 : MathUtils.random(1, skillLevel);
    int otherRoll = MathUtils.random(1, 6);
    int hitRoll = skillRoll > otherRoll ? skillRoll : otherRoll;

    // Add accuracy

    AttributesComponent starterAttributes = ComponentMappers.attributes.get(starter);

    hitRoll += starterAttributes.agility == 0 ? 0 : MathUtils.random(1, starterAttributes.agility);

    // Roll their dodge

    AttributesComponent targetAttributes = ComponentMappers.attributes.get(target);
    int dodgeRoll = MathUtils.random(1, targetAttributes.agility);

    // Miss if under

    if (hitRoll < dodgeRoll) {
      return 0;
    }

    // Roll target body part

    BodyComponent targetBody = ComponentMappers.body.get(target);
    int bodyPartRoll = MathUtils.random(1, targetBody.bodyParts.get(bodyPart));

    // Miss if under

    if (hitRoll < bodyPartRoll) {
      return 0;
    }

    // Tween some opacity on the entity hit
    VisualComponent targetVisual = ComponentMappers.visual.get(target);

    WorldManager.tweens.add(
        Tween.to(targetVisual.sprite, SpriteAccessor.ALPHA, .05f)
            .target(.25f).repeatYoyo(1, 0f)
    );

    // Ya didn't miss!
    return hitRoll;
  }

  private int rollDamage(AttackType attackType, Entity starter, Entity target,
                         Entity item, int hitRoll, String bodyPart) {
    int baseDamage = 0;

    switch (attackType) {
      case MELEE:
        AttributesComponent starterAttributes = ComponentMappers.attributes.get(starter);

        baseDamage = MathUtils.random(1, starterAttributes.strength);

        if (item != null) {
          baseDamage += MathUtils.random(
              1, ComponentMappers.item.get(item).attributes.get("hitDamage")
          );
        }

        break;
      case RANGE:
        if (item != null) {
          baseDamage += MathUtils.random(
              1, ComponentMappers.item.get(item).attributes.get("shotDamage")
          );
        }

        break;
      case THROW:
        if (item != null) {
          baseDamage += MathUtils.random(
              1, ComponentMappers.item.get(item).attributes.get("throwDamage")
          );
        }

        break;
      default:
    }

    // If your hit roll was >= 8, you critical (add a 6 roll to the damage)

    int critDamage = 0;

    if (hitRoll >= 8) {
      critDamage = MathUtils.random(1, 6);
    }

    // If it was a successful head shot (add a 6 roll to the damage)

    int headShotDamage = 0;

    if (Objects.equals(bodyPart, "head")) {
      headShotDamage = MathUtils.random(1, 6);
    }

    int totalDamage = baseDamage + critDamage + headShotDamage;

    // Modify based on weapon quality

    if (item != null) {
      ItemComponent itemDetails = ComponentMappers.item.get(item);

      totalDamage += itemDetails.quality.getModifier();

      if (itemDetails.stoneMaterial != null) {
        totalDamage += itemDetails.stoneMaterial.getModifier();
      }
    }

    // Modify based on abilities

    if (ComponentMappers.abilities.has(starter)
        && ComponentMappers.abilities.get(starter).abilities.get("Bonus against Animals") != null
        && ComponentMappers.attributes.get(target).type == AttributesComponent.Type.ANIMAL) {
      totalDamage += MathUtils.random(1, 4);
    }

    // Modify based on wrath

    GodComponent god = ComponentMappers.god.get(WorldManager.god);
    AttributesComponent targetAttributes = ComponentMappers.attributes.get(target);

    if (ComponentMappers.player.has(target)
        && targetAttributes.divineFavor <= 0
        && god.wrath.contains("Animals do more damage")) {
      if (ComponentMappers.attributes.get(starter).type == AttributesComponent.Type.ANIMAL) {
        totalDamage += MathUtils.random(1, 8);
      }
    }

    // Make sure it ain't negative

    if (totalDamage < 0) {
      totalDamage = 0;
    }

    return totalDamage;
  }

  private void applyDamage(Entity starter, Entity target, Entity item,
                           int damage, String skill, String bodyPart, boolean isFocused) {
    int defense = WorldManager.entityHelpers.getCombinedDefense(target);

    if (damage > defense) {
      AttributesComponent targetAttributes = ComponentMappers.attributes.get(target);

      // Update attacksToKill counter

      if (ComponentMappers.enemy.has(target)) {
        ComponentMappers.enemy.get(target).attacksToKill += 1;
      }

      // Shake camera if the player was hit

      if (ComponentMappers.player.has(target)) {
        Main.cameraShake.shake(.5f, .1f);
      }

      // Maybe add some blood to the floor

      Vector2 position = WorldManager.mapHelpers.getRandomOpenSpaceNearEntity(target);

      if (position != null) {
        WorldManager.mapHelpers.makeFloorBloody(position);
      }

      // Sound effects!

      switch (skill) {
        case "slashing":
          Main.soundManager.slashing();
          break;
        case "piercing":
          Main.soundManager.piercing();
          break;
        case "bashing":
          Main.soundManager.bashing();
          break;
        default:
          Main.soundManager.unarmed();
          break;
      }

      // Deal the damage

      BodyComponent targetBody = ComponentMappers.body.get(target);
      int totalDamage = damage - defense;

      WorldManager.entityHelpers.takeDamage(target, totalDamage);
      targetBody.damage.put(bodyPart, targetBody.damage.get(bodyPart) + totalDamage);

      if (ComponentMappers.player.has(starter)) {
        ComponentMappers.player.get(starter).totalDamageDone += totalDamage;
      }

      // Add fear to enemy

      BrainComponent brain = ComponentMappers.brain.get(target);

      if (brain != null) {
        brain.fear += (totalDamage / 100f);
      }

      // Apply status effects

      boolean addEffect = false;

      if (Objects.equals(bodyPart, "body")) {
        if (MathUtils.random() > 0.25f) {
          addEffect = targetBody.damage.get(bodyPart) > (targetAttributes.maxHealth / 2);
        }
      } else {
        if (MathUtils.random() > 0.5f) {
          addEffect = targetBody.damage.get(bodyPart) > (targetAttributes.maxHealth / 3);
        }
      }

      if (addEffect) {
        if (bodyPart.contains("leg") && !ComponentMappers.crippled.has(target)) {
          target.add(new CrippledComponent(4));
          WorldManager.log.add("effects.crippled.started", getName(starter), getName(target));
        } else if (bodyPart.contains("body") && !ComponentMappers.bleeding.has(target)) {
          target.add(new BleedingComponent(5, 5));
          WorldManager.log.add("effects.bleeding.started", getName(starter), getName(target));
        }
      }

      AttributesComponent starterAttributes = ComponentMappers.attributes.get(starter);

      // Chance to raise agility if focused
      if (isFocused && MathUtils.random() > .75) {
        if (starterAttributes.agility < 12) {
          starterAttributes.agility
              = starterAttributes.agility == 0 ? 4 : starterAttributes.agility + 2;
        }

        if (ComponentMappers.player.has(starter)) {
          WorldManager.log.add("skills.increased", "agility");
        }
      }

      // Give experience

      SkillsComponent skills = ComponentMappers.skills.get(starter);
      skills.counters.put(skill, skills.counters.get(skill) + 20);

      int skillLevel = skills.levels.get(skill);
      int expNeeded = skillLevel == 0 ? 40 : ((skillLevel + 2) * 100);

      if (skills.counters.get(skill) >= expNeeded && skillLevel < 12) {
        skills.levels.put(skill, skillLevel == 0 ? 4 : skillLevel + 2);
        skills.counters.put(skill, 0);

        if (ComponentMappers.player.has(starter)) {
          WorldManager.log.add("skills.increased", skill);
        }

        if (MathUtils.random() > .25) {
          switch (skills.associations.get(skill)) {
            case "agility":
              if (starterAttributes.agility < 12) {
                starterAttributes.agility
                    = starterAttributes.agility == 0 ? 4 : starterAttributes.agility + 2;
              }
              break;
            case "strength":
              if (starterAttributes.strength < 12) {
                starterAttributes.strength
                    = starterAttributes.strength == 0 ? 4 : starterAttributes.strength + 2;
              }
              break;
            case "toughness":
              if (starterAttributes.toughness < 12) {
                starterAttributes.toughness
                    = starterAttributes.toughness == 0 ? 4 : starterAttributes.toughness + 2;
              }
              break;
            default:
          }

          if (ComponentMappers.player.has(starter)) {
            WorldManager.log.add("attributes.increased", skills.associations.get(skill));
          }
        }
      }

      // Apply entity status effects

      if (ComponentMappers.effects.has(starter)) {
        EffectsComponent starterEffects = ComponentMappers.effects.get(starter);

        for (Effect effect : starterEffects.effects) {
          if (effect.trigger == Effect.Trigger.HIT) {
            effect.act(starter, target);
          }
        }
      }

      // Apply weapon effects

      if (item != null) {
        ItemComponent itemDetails = ComponentMappers.item.get(item);

        if (ComponentMappers.effects.has(item)) {
          EffectsComponent itemEffects = ComponentMappers.effects.get(item);

          for (Effect effect : itemEffects.effects) {
            if (effect.trigger == Effect.Trigger.HIT) {
              effect.act(starter, target);
            }
          }

          if (ComponentMappers.player.has(starter)) {
            PlayerComponent playerDetails = ComponentMappers.player.get(starter);

            if (playerDetails != null
                && !playerDetails.identifiedItems.contains(itemDetails.name, true)) {
              playerDetails.identifiedItems.add(itemDetails.name);
            }
          }
        }
      }

      // Log some shit

      WorldManager.log.add(
          "combat.hit", getName(starter),
          (item == null ? "hit" : ComponentMappers.item.get(item).verbs.random()),
          getName(target), totalDamage, bodyPart
      );

      // Kill it?

      if (targetAttributes.health <= 0) {
        if (ComponentMappers.player.has(starter)) {
          PlayerComponent playerDetails = ComponentMappers.player.get(starter);

          playerDetails.lastHitEntity = null;
          playerDetails.totalKills += 1;

          if (ComponentMappers.god.get(WorldManager.god).hates.contains("Unworthy prey")
              && ComponentMappers.enemy.get(target).attacksToKill <= 10) {
            ComponentMappers.attributes.get(starter).divineFavor -= MathUtils.random(1, 10);

            WorldManager.log.add(
                "attributes.divineFavor.decreased",
                ComponentMappers.god.get(WorldManager.god).name
            );
          }

          WorldManager.log.add("combat.playerKilledEnemy", targetAttributes.name);
        } else {
          WorldManager.log.add("combat.enemyKilledPlayer", starterAttributes.name);
        }
      }
    }
  }

  private String getName(Entity entity) {
    return ComponentMappers.player.has(entity)
        ? "You" : ComponentMappers.attributes.get(entity).name;
  }

  private enum AttackType {
    MELEE, RANGE, THROW
  }
}
