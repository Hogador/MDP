def test_add_pos():
    assert add(2, 3) == 5

def test_add_neg():
    assert add(-2, -3) == -5

def test_add_zero():
    assert add(0, 0) == 0

test_add_pos()
test_add_neg()
test_add_zero()

print("SMOKE_OK")