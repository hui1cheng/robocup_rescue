/**
 * 中心代理的包
 * <p>
 * 这种类型的代理与世界的唯一交互是通过无线电通信,它们在仿真服务器中作为建筑物存在<br>
 * 中心代理共有三种类型:
 * <ul>
 *     <li>救护中心
 *     <li>消防局
 *     <li>警察局
 * </ul>
 * <p>
 * 它们主要有以下两种算法:
 * <ul>
 *     <li>命令选择器({@link adf.core.component.centralized.CommandPicker})
 *     <li>命令执行器({@link adf.core.component.centralized.CommandExecutor})
 * </ul>
 *
 * @see rangel.centralized.ambulance.RangelCommandExecutorAmbulance
 * @see rangel.centralized.fire.RangelCommandExecutorFire
 * @see rangel.centralized.police.RangelCommandExecutorPolice
 * @see rangel.centralized.ambulance.RangelCommandExecutorScoutAmbulance
 * @see rangel.centralized.fire.RangelCommandExecutorScoutFire
 * @see rangel.centralized.police.RangelCommandExecutorScoutPolice
 * @see rangel.centralized.ambulance.RangelCommandPickerAmbulance
 * @see rangel.centralized.fire.RangelCommandPickerFire
 * @see rangel.centralized.police.RangelCommandPickerPolice
 */
package rangel.centralized;