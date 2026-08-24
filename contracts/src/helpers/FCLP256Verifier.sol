// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title FCLP256Verifier
/// @notice Pure-Solidity P-256 (secp256r1) verifier wrapping FCL (Fresh CryptoLib).
///         Matches the EIP-7212 precompile interface via fallback, and also exposes
///         an explicit verify() function for direct calls.
/// @dev Based on Daimo's P256Verifier (MIT):
///      https://github.com/daimo-eth/p256-verifier/blob/master/src/P256Verifier.sol
///      Which itself is based on Ledger's FreshCryptoLib:
///      https://github.com/rdubois-crypto/FreshCryptoLib/tree/master/solidity
contract FCLP256Verifier {
    /// @notice Verify a P-256 signature via fallback (EIP-7212 compatible).
    ///         input[  0: 32] = signed data hash
    ///         input[ 32: 64] = signature r
    ///         input[ 64: 96] = signature s
    ///         input[ 96:128] = public key x
    ///         input[128:160] = public key y
    ///         result[ 0: 32] = 0 (invalid) or 1 (valid)
    fallback(bytes calldata input) external returns (bytes memory) {
        if (input.length != 160) {
            return abi.encodePacked(uint256(0));
        }

        bytes32 hash = bytes32(input[0:32]);
        uint256 r = uint256(bytes32(input[32:64]));
        uint256 s = uint256(bytes32(input[64:96]));
        uint256 px = uint256(bytes32(input[96:128]));
        uint256 py = uint256(bytes32(input[128:160]));

        uint256 ret = ecdsa_verify(hash, r, s, px, py) ? 1 : 0;
        return abi.encodePacked(ret);
    }

    /// @notice Explicit verify function matching SocialRecoveryModule's expected interface.
    /// @param hash The signed data hash
    /// @param r Signature component r
    /// @param s Signature component s
    /// @param px Public key coordinate x
    /// @param py Public key coordinate y
    /// @return 1 if valid, 0 if invalid
    function verify(bytes32 hash, bytes32 r, bytes32 s, bytes32 px, bytes32 py) external view returns (uint256) {
        return ecdsa_verify(hash, uint256(r), uint256(s), uint256(px), uint256(py)) ? 1 : 0;
    }

    // ──────────────────────────────────────────────
    //  secp256r1 (P-256) Curve Parameters
    // ──────────────────────────────────────────────

    /// @notice Curve prime field modulus
    uint256 constant p = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF;
    /// @notice Short Weierstrass first coefficient (a = -3 mod p)
    uint256 constant a = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC;
    /// @notice Short Weierstrass second coefficient
    uint256 constant b = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B;
    /// @notice Generator point x
    uint256 constant GX = 0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296;
    /// @notice Generator point y
    uint256 constant GY = 0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5;
    /// @notice Curve order (number of points)
    uint256 constant n = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551;
    /// @notice -2 mod p
    uint256 constant minus_2modp = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFD;
    /// @notice -2 mod n
    uint256 constant minus_2modn = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254F;

    // ──────────────────────────────────────────────
    //  ECDSA Verification
    // ──────────────────────────────────────────────

    /// @dev ECDSA verification given message hash, signature (r, s), and public key (px, py).
    function ecdsa_verify(
        bytes32 message_hash,
        uint256 r,
        uint256 s,
        uint256 px,
        uint256 py
    ) private view returns (bool) {
        // Check r and s are in the scalar field
        if (r == 0 || r >= n || s == 0 || s >= n) {
            return false;
        }

        if (!ecAff_isValidPubkey(px, py)) {
            return false;
        }

        uint256 sInv = nModInv(s);

        uint256 scalar_u = mulmod(uint256(message_hash), sInv, n);
        uint256 scalar_v = mulmod(r, sInv, n);

        uint256 r_x = ecZZ_mulmuladd(px, py, scalar_u, scalar_v);
        return r_x % n == r;
    }

    // ──────────────────────────────────────────────
    //  Public Key Validation
    // ──────────────────────────────────────────────

    /// @dev Check if a point in affine coordinates is a valid P-256 public key.
    function ecAff_isValidPubkey(uint256 x, uint256 y) internal pure returns (bool) {
        if (x >= p || y >= p || (x == 0 && y == 0)) {
            return false;
        }
        return ecAff_satisfiesCurveEqn(x, y);
    }

    /// @dev Check if (x, y) satisfies the curve equation y^2 = x^3 + a*x + b.
    function ecAff_satisfiesCurveEqn(uint256 x, uint256 y) internal pure returns (bool) {
        uint256 LHS = mulmod(y, y, p);
        uint256 RHS = addmod(mulmod(mulmod(x, x, p), x, p), mulmod(a, x, p), p);
        RHS = addmod(RHS, b, p);
        return LHS == RHS;
    }

    // ──────────────────────────────────────────────
    //  Strauss-Shamir Multiplication (u*G + v*Q)
    // ──────────────────────────────────────────────

    /// @dev Computation of u*G + v*Q using Strauss-Shamir's trick.
    ///      Returns only the x-coordinate of u*G + v*Q.
    function ecZZ_mulmuladd(
        uint256 QX,
        uint256 QY,
        uint256 scalar_u,
        uint256 scalar_v
    ) internal view returns (uint256 X) {
        uint256 zz = 1;
        uint256 zzz = 1;
        uint256 Y;
        uint256 HX;
        uint256 HY;

        if (scalar_u == 0 && scalar_v == 0) return 0;

        // H = G + Q
        (HX, HY) = ecAff_add(GX, GY, QX, QY);

        int256 index = 255;
        uint256 bitpair;

        // Find the first active bit
        while (index >= 0) {
            bitpair = compute_bitpair(uint256(index), scalar_u, scalar_v);
            index--;
            if (bitpair != 0) break;
        }

        // Initialize (X, Y) based on first active bitpair
        if (bitpair == 1) {
            (X, Y) = (GX, GY);
        } else if (bitpair == 2) {
            (X, Y) = (QX, QY);
        } else if (bitpair == 3) {
            (X, Y) = (HX, HY);
        }

        uint256 TX;
        uint256 TY;
        while (index >= 0) {
            (X, Y, zz, zzz) = ecZZ_double_zz(X, Y, zz, zzz);

            bitpair = compute_bitpair(uint256(index), scalar_u, scalar_v);
            index--;

            if (bitpair == 0) {
                continue;
            } else if (bitpair == 1) {
                (TX, TY) = (GX, GY);
            } else if (bitpair == 2) {
                (TX, TY) = (QX, QY);
            } else {
                (TX, TY) = (HX, HY);
            }

            (X, Y, zz, zzz) = ecZZ_dadd_affine(X, Y, zz, zzz, TX, TY);
        }

        uint256 zzInv = pModInv(zz);
        X = mulmod(X, zzInv, p);
    }

    /// @dev Compute 2-bit concatenation of u[index] and v[index].
    function compute_bitpair(uint256 index, uint256 scalar_u, uint256 scalar_v) internal pure returns (uint256 ret) {
        ret = (((scalar_v >> index) & 1) << 1) + ((scalar_u >> index) & 1);
    }

    // ──────────────────────────────────────────────
    //  Point Addition (Affine)
    // ──────────────────────────────────────────────

    /// @dev Add two affine points. Returns result in affine coordinates.
    function ecAff_add(uint256 x1, uint256 y1, uint256 x2, uint256 y2) internal view returns (uint256, uint256) {
        uint256 zz1;
        uint256 zzz1;

        if (ecAff_IsInf(x1, y1)) return (x2, y2);
        if (ecAff_IsInf(x2, y2)) return (x1, y1);

        (x1, y1, zz1, zzz1) = ecZZ_dadd_affine(x1, y1, 1, 1, x2, y2);

        return ecZZ_SetAff(x1, y1, zz1, zzz1);
    }

    /// @dev Check if affine point is infinity.
    function ecAff_IsInf(uint256 x, uint256 y) internal pure returns (bool) {
        return (x == 0 && y == 0);
    }

    /// @dev Check if ZZ point is infinity.
    function ecZZ_IsInf(uint256 zz, uint256 zzz) internal pure returns (bool) {
        return (zz == 0 && zzz == 0);
    }

    // ──────────────────────────────────────────────
    //  ZZ-Affine Addition (madd-2008-s)
    // ──────────────────────────────────────────────

    /// @dev Add a ZZ point to an affine point. Returns ZZ rep.
    ///      Uses madd-2008-s / mdbl-2008-s from hyperelliptic.org.
    function ecZZ_dadd_affine(
        uint256 x1,
        uint256 y1,
        uint256 zz1,
        uint256 zzz1,
        uint256 x2,
        uint256 y2
    ) internal pure returns (uint256 x3, uint256 y3, uint256 zz3, uint256 zzz3) {
        if (ecAff_IsInf(x2, y2)) {
            if (ecZZ_IsInf(zz1, zzz1)) return ecZZ_PointAtInf();
            return (x1, y1, zz1, zzz1);
        } else if (ecZZ_IsInf(zz1, zzz1)) {
            return (x2, y2, 1, 1);
        }

        uint256 comp_R = addmod(mulmod(y2, zzz1, p), p - y1, p); // R = S2 - y1 = y2*zzz1 - y1
        uint256 comp_P = addmod(mulmod(x2, zz1, p), p - x1, p); // P = U2 - x1 = x2*zz1 - x1

        if (comp_P != 0) {
            uint256 comp_PP = mulmod(comp_P, comp_P, p); // PP = P^2
            uint256 comp_PPP = mulmod(comp_PP, comp_P, p); // PPP = P*PP
            zz3 = mulmod(zz1, comp_PP, p); // ZZ3 = ZZ1*PP
            zzz3 = mulmod(zzz1, comp_PPP, p); // ZZZ3 = ZZZ1*PPP
            uint256 comp_Q = mulmod(x1, comp_PP, p); // Q = X1*PP
            x3 = addmod(
                addmod(mulmod(comp_R, comp_R, p), p - comp_PPP, p),
                mulmod(minus_2modp, comp_Q, p),
                p
            ); // R^2 - PPP - 2*Q
            y3 = addmod(
                mulmod(addmod(comp_Q, p - x3, p), comp_R, p),
                mulmod(p - y1, comp_PPP, p),
                p
            ); // R*(Q-x3) - y1*PPP
        } else if (comp_R == 0) {
            (x3, y3, zz3, zzz3) = ecZZ_double_affine(x2, y2);
        } else {
            (x3, y3, zz3, zzz3) = ecZZ_PointAtInf();
        }
    }

    // ──────────────────────────────────────────────
    //  Point Doubling (dbl-2008-s-1)
    // ──────────────────────────────────────────────

    /// @dev Double a ZZ point. Uses dbl-2008-s-1.
    function ecZZ_double_zz(
        uint256 x1,
        uint256 y1,
        uint256 zz1,
        uint256 zzz1
    ) internal pure returns (uint256 x3, uint256 y3, uint256 zz3, uint256 zzz3) {
        if (ecZZ_IsInf(zz1, zzz1)) return ecZZ_PointAtInf();

        uint256 comp_U = mulmod(2, y1, p); // U = 2*Y1
        uint256 comp_V = mulmod(comp_U, comp_U, p); // V = U^2
        uint256 comp_W = mulmod(comp_U, comp_V, p); // W = U*V
        uint256 comp_S = mulmod(x1, comp_V, p); // S = X1*V
        uint256 comp_M = addmod(mulmod(3, mulmod(x1, x1, p), p), mulmod(a, mulmod(zz1, zz1, p), p), p);

        x3 = addmod(mulmod(comp_M, comp_M, p), mulmod(minus_2modp, comp_S, p), p);
        y3 = addmod(mulmod(comp_M, addmod(comp_S, p - x3, p), p), mulmod(p - comp_W, y1, p), p);
        zz3 = mulmod(comp_V, zz1, p);
        zzz3 = mulmod(comp_W, zzz1, p);
    }

    /// @dev Double an affine point. Returns ZZ rep. Uses mdbl-2008-s-1.
    function ecZZ_double_affine(uint256 x1, uint256 y1) internal pure returns (uint256 x3, uint256 y3, uint256 zz3, uint256 zzz3) {
        if (ecAff_IsInf(x1, y1)) return ecZZ_PointAtInf();

        uint256 comp_U = mulmod(2, y1, p); // U = 2*Y1
        zz3 = mulmod(comp_U, comp_U, p); // V = U^2 = zz3
        zzz3 = mulmod(comp_U, zz3, p); // W = U*V = zzz3
        uint256 comp_S = mulmod(x1, zz3, p); // S = X1*V
        uint256 comp_M = addmod(mulmod(3, mulmod(x1, x1, p), p), a, p);

        x3 = addmod(mulmod(comp_M, comp_M, p), mulmod(minus_2modp, comp_S, p), p);
        y3 = addmod(mulmod(comp_M, addmod(comp_S, p - x3, p), p), mulmod(p - zzz3, y1, p), p);
    }

    // ──────────────────────────────────────────────
    //  Coordinate Conversion
    // ──────────────────────────────────────────────

    /// @dev Convert ZZ rep to affine rep.
    function ecZZ_SetAff(uint256 x, uint256 y, uint256 zz, uint256 zzz) internal view returns (uint256 x1, uint256 y1) {
        if (ecZZ_IsInf(zz, zzz)) {
            return (0, 0);
        }

        uint256 zzzInv = pModInv(zzz); // 1 / zzz
        uint256 zInv = mulmod(zz, zzzInv, p); // 1 / z
        uint256 zzInv = mulmod(zInv, zInv, p); // 1 / zz

        x1 = mulmod(x, zzInv, p); // X / zz
        y1 = mulmod(y, zzzInv, p); // Y / zzz
    }

    /// @dev Point at infinity in ZZ rep.
    function ecZZ_PointAtInf() internal pure returns (uint256, uint256, uint256, uint256) {
        return (0, 0, 0, 0);
    }

    // ──────────────────────────────────────────────
    //  Modular Inversion (via ModExp precompile 0x05)
    // ──────────────────────────────────────────────

    /// @dev u^-1 mod n
    function nModInv(uint256 u) internal view returns (uint256) {
        return modInv(u, n, minus_2modn);
    }

    /// @dev u^-1 mod p
    function pModInv(uint256 u) internal view returns (uint256) {
        return modInv(u, p, minus_2modp);
    }

    /// @dev Compute u^(f-2) mod f using ModExp precompile (0x05).
    function modInv(uint256 u, uint256 f, uint256 minus_2modf) internal view returns (uint256 result) {
        (bool success, bytes memory ret) = address(0x05).staticcall(abi.encode(32, 32, 32, u, minus_2modf, f));
        assert(success);
        result = abi.decode(ret, (uint256));
    }
}
