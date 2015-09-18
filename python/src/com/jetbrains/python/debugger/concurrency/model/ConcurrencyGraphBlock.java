/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger.concurrency.model;

import java.awt.*;
import java.util.ArrayList;

public class ConcurrencyGraphBlock {
  public ArrayList<ConcurrencyGraphElement> elements;
  public int numberOfCells;
  public Point relation;

  public ConcurrencyGraphBlock(ArrayList<ConcurrencyGraphElement> elements, int numberOfCells, Point relation) {
    this.elements = elements;
    this.numberOfCells = numberOfCells;
    this.relation = relation;
  }
}
