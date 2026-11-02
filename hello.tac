func abs, 1
    return a
endfunc
func val, 2
    t1 = d < 0
    ifFalse t1 goto L1
    param d
    call abs, 1
L1:
    t2 = t < 0
    ifFalse t2 goto L2
    param t
    call abs, 1
L2:
    t3 = d / t
    v = t3
    return v
endfunc
func main, 0
    param 5
    param 3
    call val, 2
    return 0
endfunc
