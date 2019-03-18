"use strict";

const { expect } = require("chai");
const javaParser = require("../src/index");

describe("The Java Parser fixed bugs", () => {
  it("issue #129 - this.<bar>.baz()", () => {
    const input = "this.<Number>anyIterableType()";
    expect(() => javaParser.parse(input, "expression")).to.not.throw();
  });
});

describe("The Java Parser fixed bugs", () => {
  it("issue #131 - 1.0e-10", () => {
    const input = "1.0e-10";
    expect(() => javaParser.parse(input, "expression")).to.not.throw();
  });
});

describe("The Java Parser fixed bugs", () => {
  it("issue #130 - enumConstantList", () => {
    const input = "enum Foo { BAR, }";
    expect(() => javaParser.parse(input)).to.not.throw();
  });
});

describe("The Java Parser fixed bugs", () => {
  it("issue #158 - semicolons inside imports", () => {
    const input = "import Foo;;import Bar;";
    expect(() =>
      javaParser.parse(input, "ordinaryCompilationUnit")
    ).to.not.throw();
  });
});

describe("The Java Parser fixed bugs", () => {
  it("cast expression, parenthesis and binary shift", () => {
    const input = "(left) << right";
    expect(() => javaParser.parse(input, "expression")).to.not.throw();
  });

  it("cast expression, parenthesis and lessThan", () => {
    const input = "(left) < right";
    expect(() => javaParser.parse(input, "expression")).to.not.throw();
  });
});
