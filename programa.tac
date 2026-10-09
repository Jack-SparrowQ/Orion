    a = 10
    b = 3
    t1 = b * 2
    t2 = a + t1
    c = t2
    t3 = c > 5
    t4 = !false
    t5 = t3 && t4
    ifFalse t5 goto L1
    print c
    t6 = a != b
    ifFalse t6 goto L2
    t7 = a - b
    print t7
L2:
L1:
    t8 = a + b
    t9 = t8 * 2
    print t9
