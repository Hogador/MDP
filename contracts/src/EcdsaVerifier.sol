// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {ITrustProvider} from "./ITrustProvider.sol";

contract EcdsaVerifier is ITrustProvider {
    using ECDSA for bytes32;

    address public immutable signer;

    error InvalidSigner();

    constructor(address _signer) {
        if (_signer == address(0)) revert InvalidSigner();
        signer = _signer;
    }

    function verify(bytes32 intentHash, bytes calldata proof) external view returns (bool) {
        return intentHash.recover(proof) == signer;
    }
}
