package v0id.vsb.item.upgrade;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.CapabilityItemHandler;
import v0id.api.vsb.capability.IFilter;
import v0id.api.vsb.data.VSBRegistryNames;
import v0id.api.vsb.item.IBackpackWrapper;
import v0id.api.vsb.item.IUpgradeWrapper;
import v0id.vsb.util.Lazy;
import v0id.vsb.util.VSBUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UpgradeFeeding extends UpgradeFiltered
{
    private final Field fieldItemFood_healAmount = VSBUtils.getFieldSafe(ItemFood.class, "healAmount", "field_77853_b");
    private final Lazy<Class<?>> class_NutrientUtils = new Lazy<>(() -> VSBUtils.getOptionalClass("ca.wescook.nutrition.nutrients.NutrientUtils", () -> Loader.isModLoaded("nutrition")));
    private final Lazy<Class<?>> class_CapabilityManager = new Lazy<>(() -> VSBUtils.getOptionalClass("ca.wescook.nutrition.capabilities.CapabilityManager", () -> Loader.isModLoaded("nutrition")));
    private final Lazy<Class<?>> class_INutrientManager = new Lazy<>(() -> VSBUtils.getOptionalClass("ca.wescook.nutrition.capabilities.INutrientManager", () -> Loader.isModLoaded("nutrition")));
    private final Lazy<Field> capabilityManager_NutritionCapability = new Lazy<>(() -> VSBUtils.getFieldSafe(class_CapabilityManager.get(), "NUTRITION_CAPABILITY"));
    private final Lazy<Method> nutrientUtils_getFoodNutrients = new Lazy<>(() -> VSBUtils.getMethodSafe(class_NutrientUtils.get(), new Class[]{ ItemStack.class }, "getFoodNutrients"));
    private final Lazy<Method> nutrientUtils_calculateNutrition = new Lazy<>(() -> VSBUtils.getMethodSafe(class_NutrientUtils.get(), new Class[]{ ItemStack.class, List.class }, "calculateNutrition"));
    private final Lazy<Method> iNutrientManager_add = new Lazy<>(() -> VSBUtils.getMethodSafe(class_INutrientManager.get(), new Class[]{ List.class, float.class }, "add"));
    private final Lazy<Capability> nutritionCapability = new Lazy<>(() -> (Capability) VSBUtils.getFieldValue(capabilityManager_NutritionCapability.get(), null));

    public UpgradeFeeding()
    {
        super(VSBRegistryNames.itemUpgradeFeeding);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.addAll(Arrays.asList(I18n.format("vsb.txt.upgrade.feeding.desc").split("\\|")));
    }

    @Override
    public void onTick(@Nullable IBackpackWrapper container, IBackpackWrapper backpack, IUpgradeWrapper self, Entity ticker)
    {
    }

    @Override
    public void onPulse(@Nullable IBackpackWrapper container, IBackpackWrapper backpack, IUpgradeWrapper self, Entity pulsar)
    {
        if (pulsar instanceof EntityPlayer)
        {
            FoodStats foodStats = ((EntityPlayer) pulsar).getFoodStats();
            int needed = 20 - foodStats.getFoodLevel();
            if (needed > 0)
            {
                int highestSlot = -1;
                int maxHealAmount = -1;
                for (int i = 0; i < backpack.getInventory().getSlots(); ++i)
                {
                    ItemStack is = backpack.getInventory().getStackInSlot(i);
                    if (!is.isEmpty() && is.getItem() instanceof ItemFood)
                    {
                        IFilter filter = IFilter.of(self.getSelf().getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).getStackInSlot(0));
                        if (filter != null && !filter.accepts(is))
                        {
                            continue;
                        }

                        int healAmount = 0;
                        try
                        {
                            healAmount = fieldItemFood_healAmount.getInt(is.getItem());
                        }
                        catch (IllegalAccessException e)
                        {
                            fieldItemFood_healAmount.setAccessible(true);
                            try
                            {
                                healAmount = fieldItemFood_healAmount.getInt(is.getItem());
                            }
                            catch (IllegalAccessException e1)
                            {
                                FMLCommonHandler.instance().raiseException(e1, "An impossible reflection exception has occurred.", true);
                            }
                        }

                        if (healAmount > maxHealAmount && healAmount <= needed)
                        {
                            maxHealAmount = healAmount;
                            highestSlot = i;
                            if (healAmount == needed)
                            {
                                break;
                            }
                        }
                    }
                }

                if (highestSlot != -1)
                {
                    ItemStack food = backpack.getInventory().getStackInSlot(highestSlot);
                    ItemFood foodItem = (ItemFood) food.getItem();
                    ItemStack stack = food.copy();
                    stack.setCount(1);
                    foodItem.onItemUseFinish(food, pulsar.world, (EntityLivingBase) pulsar);
                    backpack.markInventoryDirty();
                    if (Loader.isModLoaded("nutrition"))
                    {
                        try
                        {
                            List nutrients = (List) VSBUtils.invokeMethod(nutrientUtils_getFoodNutrients.get(), null, stack);
                            float nutValue = (float) VSBUtils.invokeMethod(nutrientUtils_calculateNutrition.get(), null, stack, nutrients);
                            Object cap = pulsar.getCapability(nutritionCapability.get(), null);
                            VSBUtils.invokeMethod(iNutrientManager_add.get(), cap, nutrients, nutValue);
                        }
                        catch (Exception ex)
                        {
                            FMLCommonHandler.instance().raiseException(ex, "VSB caught an error trying to reflect Nutrition", false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onItemPickup(@Nullable IBackpackWrapper container, IBackpackWrapper backpack, IUpgradeWrapper self, EntityItem item, Entity picker)
    {
        return false;
    }

    @Override
    public void onInstalled(IBackpackWrapper backpack, IUpgradeWrapper self)
    {
    }

    @Override
    public void onUninstalled(IBackpackWrapper backpack, IUpgradeWrapper self)
    {
    }

    @Override
    public boolean canInstall(IBackpackWrapper backpack, IUpgradeWrapper self)
    {
        return !Arrays.stream(backpack.getReadonlyUpdatesArray()).filter(Objects::nonNull).map(IUpgradeWrapper::getSelf).anyMatch(i -> i.getItem() == self.getSelf().getItem());
    }

    @Override
    public boolean hasSyncTag()
    {
        return false;
    }
}
