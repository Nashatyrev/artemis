/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ssz.backing.collections.impl;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.collections.SszMutableUInt64List;
import tech.pegasys.teku.ssz.backing.collections.SszUInt64List;
import tech.pegasys.teku.ssz.backing.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class SszUInt64ListImpl extends SszPrimitiveListImpl<UInt64, SszUInt64>
    implements SszUInt64List {

  public SszUInt64ListImpl(SszUInt64ListSchema<?> schema, Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  public SszUInt64ListImpl(SszUInt64ListSchema<?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  @Override
  public SszMutableUInt64List createWritableCopy() {
    return new SszMutableUInt64ListImpl(this);
  }
}
