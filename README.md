# Aiken Java Binding

Java binding for [Aiken](https://aiken-lang.org/), a modern smart contract platform for Cardano Blockchain.

This library enables Java apps to evaluate script costs without relying on external services. 
It uses [aiken-jna-wrapper](https://github.com/bloxbean/aiken-jna-wrapper) to access Aiken Rust libraries.

**Current Version:** 0.0.2

### Current Limitations
- The current version doesn't work for a transaction with reference inputs. [Issue](https://github.com/bloxbean/aiken-java-binding/issues/1)

**Workaround :** During the transaction cost evaluation, remove the reference inputs from the transaction and add the plutus script to the witness set. After calculating the script cost, you can add back the reference input again.

## Supported Operating Systems / Archs
- Apple MacOS (Intel and Apple Silicon)
- Linux (x86_64) (Ubuntu 20.04 or compatible ...)
- Windows 64bits (x86_64)

For another platform, please create a PR / request [here](https://github.com/bloxbean/aiken-jna-wrapper/issues)

## Dependencies

**Maven (pom.xml)**
```xml

<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>aiken-java-binding</artifactId>
    <version>${version}</version>
</dependency>
```

**Gradle (build.gradle)**

```shell
implementation 'com.bloxbean.cardano:aiken-java-binding:${version}'
```

### How to use ?

You can use the ``TxEvaluator`` class to evaluate script execution costs for a transaction. The ``evaluateTx`` method returns a list 
of redeemers with updated execution units. You can set these execution units in the final transaction's redeemers before submitting the transaction.

```java
 TxEvaluator txEvaluator = new TxEvaluator();
 CostMdls costMdls = new CostMdls();
 
 //Get cost models from protocol parameters or provide hardcoded value
 costMdls.add(CostModelUtil.getCostModelFromProtocolParams(protocolParamsSupplier.getProtocolParams(), Language.PLUTUS_V2).orElseThrow());

 //Evaluate
 List<Redeemer> evalReedemers = txEvaluator.evaluateTx(txn, inputUtxos, costMdls);

```

# Any questions, ideas or issues?

- Create a Github [Issue](https://github.com/bloxbean/aiken-java-binding/issues)
- [Discord Server](https://discord.gg/JtQ54MSw6p)

##### If this project helps you reduce time to develop on Cardano or if you just want to support this project, you can delegate to our pool:

[BLOXB](https://www.bloxbean.com/cardano-staking/)

[Support this project](https://cardano-client.bloxbean.com/docs/support-this-project)
