"use strict";
/* eslint-disable no-unused-vars */

const _ = require("lodash");

const {
  concat,
  join,
  line,
  ifBreak,
  group,
  indent,
  dedent
} = require("prettier").doc.builders;
const {
  rejectAndJoin,
  rejectAndConcat,
  sortClassTypeChildren,
  getImageWithComments
} = require("./printer-utils");

class TypesValuesAndVariablesPrettierVisitor {
  primitiveType(ctx) {
    const annotations = this.mapVisit(ctx.annotation);
    const type = ctx.numericType
      ? this.visit(ctx.numericType)
      : getImageWithComments(this.getSingle(ctx));

    return rejectAndJoin(" ", [join(" ", annotations), type]);
  }

  numericType(ctx) {
    return this.visitSingle(ctx);
  }

  integralType(ctx) {
    return getImageWithComments(this.getSingle(ctx));
  }

  floatingPointType(ctx) {
    return getImageWithComments(this.getSingle(ctx));
  }

  referenceType(ctx) {
    const annotations = this.mapVisit(ctx.annotation);

    const type = ctx.primitiveType
      ? this.visit(ctx.primitiveType)
      : this.visit(ctx.classOrInterfaceType);

    const dims = this.visit(ctx.dims);

    return rejectAndJoin(" ", [join(" ", annotations), concat([type, dims])]);
  }

  classOrInterfaceType(ctx) {
    return this.visitSingle(ctx);
  }

  classType(ctx) {
    const tokens = sortClassTypeChildren(
      ctx.annotation,
      ctx.typeArguments,
      ctx.Identifier
    );

    const segments = [];
    let currentSegment = [];

    _.forEach(tokens, (token, i) => {
      if (token.name === "typeArguments") {
        currentSegment.push(this.visit([token]));
        segments.push(rejectAndConcat(currentSegment));
        currentSegment = [];
      } else if (token.name === "annotation") {
        currentSegment.push(this.visit([token]));
      } else {
        currentSegment.push(getImageWithComments(token));
        if (
          (i + 1 < tokens.length && tokens[i].name !== "typeArguments") ||
          i + 1 === tokens.length
        ) {
          segments.push(rejectAndConcat(currentSegment));
          currentSegment = [];
        }
      }
    });

    return rejectAndJoin(".", segments);
  }

  interfaceType(ctx) {
    return this.visitSingle(ctx);
  }

  typeVariable(ctx) {
    const annotations = this.mapVisit(ctx.annotation);
    const identifier = getImageWithComments(this.getSingle(ctx));

    return rejectAndJoin(" ", [join(" ", annotations), identifier]);
  }

  dims(ctx) {
    let tokens = [...ctx.LSquare];

    if (ctx.annotation) {
      tokens = [...tokens, ...ctx.annotation];
    }

    tokens = tokens.sort((a, b) => {
      const startOffset1 = a.name
        ? a.children.At[0].startOffset
        : a.startOffset;
      const startOffset2 = b.name
        ? b.children.At[0].startOffset
        : b.startOffset;
      return startOffset1 - startOffset2;
    });

    const segments = [];
    let currentSegment = [];

    _.forEach(tokens, token => {
      if (token.name === "annotation") {
        currentSegment.push(this.visit([token]));
      } else {
        segments.push(
          rejectAndConcat([rejectAndJoin(" ", currentSegment), "[]"])
        );
        currentSegment = [];
      }
    });

    return rejectAndConcat(segments);
  }

  typeParameter(ctx) {
    const typeParameterModifiers = this.mapVisit(ctx.typeParameterModifier);

    const typeIdentifier = this.visit(ctx.typeIdentifier);
    const typeBound = this.visit(ctx.typeBound);

    return rejectAndJoin(" ", [
      join(" ", typeParameterModifiers),
      typeIdentifier,
      typeBound
    ]);
  }

  typeParameterModifier(ctx) {
    return this.visitSingle(ctx);
  }

  typeBound(ctx) {
    const classOrInterfaceType = this.visit(ctx.classOrInterfaceType);
    const additionalBound = this.mapVisit(ctx.additionalBound);

    return rejectAndJoin(" ", [
      "extends",
      classOrInterfaceType,
      join(" ", additionalBound)
    ]);
  }

  additionalBound(ctx) {
    const interfaceType = this.visit(ctx.interfaceType);

    return join(" ", ["&", interfaceType]);
  }

  typeArguments(ctx) {
    const typeArgumentList = this.visit(ctx.typeArgumentList);

    return concat(["<", typeArgumentList, ">"]);
  }

  typeArgumentList(ctx) {
    const typeArguments = this.mapVisit(ctx.typeArgument);

    return join(", ", typeArguments);
  }

  typeArgument(ctx) {
    return this.visitSingle(ctx);
  }

  wildcard(ctx) {
    const annotations = this.mapVisit(ctx.annotation);
    const wildcardBounds = this.visit(ctx.wildcardBounds);

    return rejectAndJoin(" ", [join(" ", annotations), "?", wildcardBounds]);
  }

  wildcardBounds(ctx) {
    const keyWord = ctx.Extends ? "extends" : "super";
    const referenceType = this.visit(ctx.referenceType);
    return join(" ", [keyWord, referenceType]);
  }
}

module.exports = {
  TypesValuesAndVariablesPrettierVisitor
};
