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

package tech.pegasys.teku.ssz.backing.schema.collections;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.collections.SszByteVector;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public interface SszByteVectorSchema<SszVectorT extends SszByteVector>
    extends SszPrimitiveVectorSchema<Byte, SszByte, SszVectorT> {

  SszVectorT fromBytes(Bytes bytes);

  static SszByteVectorSchema<SszByteVector> create(int length) {
    throw new UnsupportedOperationException("TODO");
  }
}
