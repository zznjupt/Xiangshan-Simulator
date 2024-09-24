### Scala Tips


```scala
/* +: 生成新集合：头部合并*/

val list01: List[Int] = List(1, 2, 3, 4)
val list02: List[Int] = List(5, 6, 7, 8)
val list03 = "9" +: list02;
println(list03) // List(9, 5, 6, 7, 8)
val list04 = list01 +: list02;
println(list04) // List(List(1, 2, 3, 4), 5, 6, 7, 8)
```




