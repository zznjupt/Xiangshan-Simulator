# WakeupQueue 唤醒队列

> 《超标量-姚》唤醒的定义：将被select电路选中的指令的目的寄存器编号和issue queue中的其他源寄存器的编号对比（面积大），将相等的源寄存器进行标记的过程。（该部分是核心设计）

## Function

* `WakeupQueue` 模块是个FIFO，在uop入队后延迟一定的周期数出队。
* 如果在 `IusseQueue` 里配置了 `fixedLatency` 参数，则会实例化 `numDeq` 个 `WakeupQueue`。
* uop从 `IusseQueue` 出队送进FU执行时，也同时会被送入 `WakeupQueue`。当uop执行完毕，`WakeupQueue` 的延迟出队信号也同时有效，用于唤醒其它uop参与调度执行。
* 当重定向信号有效时，将会冲刷晚于ROB指针（是否等于由level信号指定）进入ROB的uop。

|        Signal        | Direction  | Width |    Source     |    Target     |                       Description                        |
| :------------------: | :--------: | :---: | :-----------: | :-----------: | :------------------------------------------------------: |
|       `clock`        |   input    |   1   |       -       | `WakeupQueue` |                                                          |
|       `reset`        |   input    |   1   |       -       | `WakeupQueue` |                                                          |
|      **io_in**       | **input**  |       |               |               | 使用`IusseQueue`入队的uop信号作为`WakeupQueue`的入队信号 |
|       `_valid`       |   input    |   1   | `IusseQueue`  | `WakeupQueue` |               `WakeupQueue`的入队信号有效                |
|  `_bits_ctrl_rfWen`  |   input    |   1   | `IusseQueue`  | `WakeupQueue` |                                                          |
|    `_bits_pdest`     |   input    | [7:0] | `IusseQueue`  | `WakeupQueue` |                                                          |
| `_bits_robIdx_flag`  |   input    |   1   | `IusseQueue`  | `WakeupQueue` |                                                          |
| `_bits_robIdx_value` |   input    |   8   | `IusseQueue`  | `WakeupQueue` |                                                          |
|      **io_out**      | **output** |       |               |               |        `WakeupQueue`的出队信号，用于唤醒相应的uop        |
|        _valid        |   output   |   1   | `WakeupQueue` | `IusseQueue`  |                       出队信号有效                       |
|   _bits_ctrl_rfWen   |   output   |   1   | `WakeupQueue` | `IusseQueue`  |                                                          |
|     _bits_pdest      |   output   | [7:0] | `WakeupQueue` | `IusseQueue`  |                                                          |
|   **io_indirect**    | **input**  |       |               |               |             用于冲刷WakeupQueue的重定向信号              |
|        _valid        |   input    |   1   | `IusseQueue`  | `WakeupQueue` |                       冲刷信号有效                       |
|  _bits_robIdx_flag   |   input    |   1   | `IusseQueue`  | `WakeupQueue` |                                                          |
|  _bits_robIdx_value  |   input    | [7:0] | `IusseQueue`  | `WakeupQueue` |                                                          |
|     _bits_level      |   input    |   1   | `IusseQueue`  | `WakeupQueue` |                                                          |

