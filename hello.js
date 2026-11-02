function abs(a) {
    return a;
}
function val(d, t) {
    if (d < 0) {
        abs(d);
    }
    if (t < 0) {
        abs(t);
    }
    let v = d / t;
    return v;
}
function main() {
    val(5, 3);
    return 0;
}
