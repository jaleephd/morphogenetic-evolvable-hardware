/* 
 * lcsstr.c, adapted by J.A. Lee, March 2003, from st.c by B. Zou
 *
 *   lcsstr takes 2 strings and returns the indexes and length of the
 *   longest common substring or a randomly chosen common substring,
 *   with longer substrings having a higher probability of being chosen
 * 
 * the suffix tree code here is adapted from the C code in file 'st.c'
 * that forms the core of the Perl String::Ediff-0.01 module, by Bo Zou
 * (boxzou@yahoo.com), released 25 Jan 2003 at www.cpan.org
 *
 */


#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <ctype.h>
/*#include <time.h> */
#include <process.h>


#define MAX_STRING	31000
#define MAXONLY		1
#define RNDONLY		-1
#define MAXNRND		0



typedef struct Suffix_Tree_Node Suffix_Tree_Node;

typedef struct Active_Point Active_Point;

struct Active_Point
{
  int m_node_id;
  int m_begin_idx;
  int m_end_idx;
};

struct Suffix_Tree_Node
{
  int m_begin_char_idx; /* inclusive */
  int m_end_char_idx;   /* inclusive */
  int m_parent;
  int m_id;
  int m_child;
  int m_sibling;
  int m_in_s1;
  int m_in_s2;
};

typedef struct Suffix_Tree Suffix_Tree;

struct Suffix_Tree
{
  Suffix_Tree_Node *m_nodes;
  int m_hash_base;
  int m_strlen;
  int m_size;
  char *m_str;
  int *m_suffix;
  Suffix_Tree_Node m_head;
};

static void ctor_node(Suffix_Tree_Node *node,
               int begin_idx, int end_idx, int parent, int id)
{
  node->m_begin_char_idx = begin_idx;
  node->m_end_char_idx = end_idx;
  node->m_parent = parent;
  node->m_id = id;
  node->m_child = -1;
  node->m_sibling = -1;
}

static void suffix_tree_cleanup(Suffix_Tree *t)
{
  free(t->m_nodes);
  free(t->m_suffix);
  t->m_hash_base = -1;
}

static void ctor_Active_Point(Active_Point *ap, int node_id,
                       int begin_idx, int end_idx)
{
  ap->m_node_id = node_id;
  ap->m_begin_idx = begin_idx;
  ap->m_end_idx = end_idx;
}

static int implicit(Active_Point *ap)
{
  return ap->m_begin_idx <= ap->m_end_idx;
}

/*
static int explicit(Active_Point *ap)
{
  return ap->m_begin_idx > ap->m_end_idx;
}
*/

static int hash(Suffix_Tree *t, int parent_id, int chr)
{
  int x = ((parent_id << 8) + chr) % t->m_hash_base;
  if (x < 0) {
    x += t->m_hash_base;
  }
  return x;
}

static int ap_span(Active_Point *ap)
{
  return ap->m_end_idx - ap->m_begin_idx;
}

static int edge_span(Suffix_Tree_Node *node)
{
  return node->m_end_char_idx - node->m_begin_char_idx;
}

static char ap_begin_char(Suffix_Tree *t, Active_Point *ap)
{
  return t->m_str[ap->m_begin_idx];
}

static char ap_end_char(Suffix_Tree *t, Active_Point *ap)
{
  return t->m_str[ap->m_end_idx];
}

static char ap_any_char(Suffix_Tree *t, Active_Point *ap, int any) 
{ 
  return t->m_str[ap->m_begin_idx + any]; 
}

static char node_begin_char(Suffix_Tree *t, Suffix_Tree_Node *node)
{
  return t->m_str[node->m_begin_char_idx];
}

static char node_end_char(Suffix_Tree *t, Suffix_Tree_Node *node)
{
  return t->m_str[node->m_end_char_idx];
}

static char node_any_char(Suffix_Tree *t, Suffix_Tree_Node *node, int any)
{
  return t->m_str[node->m_begin_char_idx + any];
}

static char node_contains(Suffix_Tree_Node *node, int pos)
{
  return node->m_begin_char_idx <= pos && pos <= node->m_end_char_idx;
}




/* find a node(edge) which is a child of 'parent_id' and begin with 'chr' */

static int find_unused_node(Suffix_Tree *t, int parent_id, int chr)
{
  int i = hash(t, parent_id, chr);
  while (1) {
    Suffix_Tree_Node *node = t->m_nodes + i;
    if (0 > node->m_id) {  /* unused slot */
      return i;
    }
    i = ++i % t->m_hash_base;
    if (i < 0) {
      i += t->m_hash_base;
    }
  }
}



/* find a node(edge) which is a child of 'parent_id' and begin with 'chr' */

static int find_node(Suffix_Tree *t, int parent_id, int chr)
{
  int i = hash(t, parent_id, chr);
  while (1) {
    Suffix_Tree_Node *node = t->m_nodes + i;
    if (-1 == node->m_id) {  /* unused slot */
      return i;
    }
    if (node->m_parent == parent_id &&
        node_begin_char(t, node) == chr) {
      return i;
    }
    /*    printf("%d \n", i); */
    i = ++i % t->m_hash_base;
    if (i < 0) {
      i += t->m_hash_base;
    }
  }
}

static void increment(int *i, int base)
{
  *i = ++*i % base;
  if (*i < 0) {
    *i += base;
  }
}

static int new_node(Suffix_Tree *t, int begin_idx, int end_idx, int parent)
{
  int i;
  t->m_size++;
  i = find_unused_node(t, parent, t->m_str[begin_idx]);
  {
    Suffix_Tree_Node *node = t->m_nodes + i;
    assert (0 > node->m_id); /* unused or removed */
    ctor_node(node, begin_idx, end_idx, parent, t->m_size);
  }
  return i;
}

static int split_edge(Suffix_Tree *t, Active_Point *ap)
{
  int node_idx, i, nid;
  Suffix_Tree_Node *node, *tmp_node;
  assert(ap);
  assert(implicit(ap));
  node_idx = find_node(t, ap->m_node_id, ap_begin_char(t, ap));
  node = t->m_nodes + node_idx;

  assert(node->m_id != -1);
  assert(edge_span(node) >= ap_span(ap));

  assert(ap_span(ap) > 0);
  assert(ap_end_char(t, ap) != node_any_char(t, node, ap_span(ap)));
  assert(ap_any_char(t, ap, ap_span(ap)-1) == node_any_char(t, node, ap_span(ap)-1));

  /*
   * match up to [ap_span(ap) - 1], see last two assertions
   * we want to reuse the existing node because parent and the first char
   * does not change, so what we create below is actually the existing node
   * We have to adjust the node "node"
   */

  i = new_node(t, node->m_begin_char_idx + ap_span(ap),
               node->m_end_char_idx,
               t->m_size+1);  /* parent is the newly created id */

  /* swap node id because the new node should be parent of the existing node. */
  tmp_node = t->m_nodes + i;
  tmp_node->m_id = node->m_id;

  node->m_id = t->m_size;
  node->m_end_char_idx = node->m_begin_char_idx + ap_span(ap) - 1;
  return t->m_size;
}

static void canonize(Suffix_Tree *t, Active_Point *ap)
{
  while (ap_span(ap) > 0) {
    int edge_len;
    int nid = find_node(t, ap->m_node_id, ap_begin_char(t, ap));
    Suffix_Tree_Node *node = t->m_nodes + nid;
    if ( node->m_id <= 0) {
      return;
    }
    edge_len = edge_span(node);
    if (edge_len > ap_span(ap) - 1) {
      return;
    }
    ap->m_node_id = node->m_id;
    ap->m_begin_idx += edge_len + 1;
  }
}

static void follow_suffix_link(Suffix_Tree *t, Active_Point *ap)
{
  if (ap->m_node_id) { /* not root */
    ap->m_node_id = t->m_suffix[ap->m_node_id];
  } else { /* root */
    ap->m_begin_idx++;
  }
  canonize(t, ap);
}

static void update(Suffix_Tree *t, Active_Point *ap)
{
  int last_parent = -1;
  while (1) {
    int node_idx = find_node(t, ap->m_node_id, ap_begin_char(t, ap));
    Suffix_Tree_Node *node = t->m_nodes + node_idx;
    assert(ap_span(ap) >= 0);

    if (node->m_id < 0) {
      assert(ap_span(ap) == 0);
      /* new node */
      new_node(t, ap->m_end_idx, t->m_strlen - 1, ap->m_node_id);
      if (last_parent > 0) {
        assert(t->m_suffix[last_parent] == ap->m_node_id
               || t->m_suffix[last_parent] == -1);
        t->m_suffix[last_parent] = ap->m_node_id;
      }
      last_parent = ap->m_node_id;
      follow_suffix_link(t, ap);
      if (ap_span(ap) < 0) break;
    } else {
      assert(edge_span(node) >= ap_span(ap) - 1);
      if (edge_span(node) == ap_span(ap) - 1) {
        
      }
      if (node_any_char(t, node, ap_span(ap)) == ap_end_char(t, ap)) { /* match */
        if (last_parent > 0) {
          /* suffix link: last_parent -> current's parent */
          t->m_suffix[last_parent] = node->m_parent;
        }
        break;
      } else { /* last char in active point not match */
        int parent;
        assert(ap_span(ap) > 0);
        assert(ap_any_char(t, ap, ap_span(ap) - 1) ==
               node_any_char(t, node, ap_span(ap) - 1));

        assert(implicit(ap));
        parent = split_edge(t, ap);

        new_node(t, ap->m_end_idx, t->m_strlen-1, parent);
        if (last_parent > 0) {
          assert(t->m_suffix[last_parent] == -1);
          t->m_suffix[last_parent] = parent;
        }
        last_parent = parent;
        follow_suffix_link(t, ap);
      }
    }
  }
}

static void print(Suffix_Tree *t)
{
  int i, j;
  for (i = 0; i < t->m_hash_base; i++) {
    Suffix_Tree_Node *node = t->m_nodes + i;
    if (node->m_id > 0) {
      printf("%4d%4d%4d%4d%4d%4d%4d  ", node->m_parent, node->m_id,
             node->m_begin_char_idx, node->m_end_char_idx, node->m_in_s1, node->m_in_s2, t->m_suffix[node->m_id]);
      for (j = node->m_begin_char_idx;
           j <= node->m_end_char_idx; j++) {
        printf("%c", t->m_str[j]);
      }
      printf("\n");
    }
  }
}

static void print_ap(Active_Point *ap)
{
  printf("%d  %d  %d\n", ap->m_node_id, ap->m_begin_idx, ap->m_end_idx);
}

static void suffix_tree_init(Suffix_Tree *t, char *str)
{
  int size = strlen(str) + 1;
  int i;
  t->m_strlen = size;
  size *= 2;
  t->m_hash_base = size+1;
  t->m_size = 0;
  t->m_nodes = (Suffix_Tree_Node*)
    malloc(sizeof(Suffix_Tree_Node)*t->m_hash_base);
  t->m_str = str;
  t->m_suffix = (int*)malloc(sizeof(int) * t->m_hash_base);

  for (i = 0; i < t->m_hash_base; i++) {
    ctor_node(t->m_nodes + i, -1, -1, -1, -1);
    t->m_suffix[i] = -1;
  }
  {
    Active_Point ap;
    ctor_Active_Point(&ap, 0, 0, 0);
    for (;ap.m_end_idx < t->m_strlen; ap.m_end_idx++) {
      canonize(t, &ap);
      update(t, &ap);
    }
  }
}

static void suffix_tree_destroy(Suffix_Tree *t)
{
  free(t->m_nodes);
  free(t->m_suffix);
}


#define END_STRING 0
#define DOLLAR_SIGN -1



/*
 * sum the length of all common substrings found in suffix tree
 * for later use in (weighting) the random choice of a common substring
 * note that for the same 2 strings, depending on their order (which is
 * put in the tree first) there may be different sum and count values!
 * This is due to the use of a suffix tree, whereby the edges are split
 * on different suffixes, while the preceding string is common.
 */

static void sum_cs_lens(Suffix_Tree *t, int s1_len, int mincs, int id, int depth, int *sum_CS_cnt, long *sum_CS_lens)
{
  Suffix_Tree_Node *node, *nc;
  int t1=-1, t2=-1;
  int len=0;
  int child;
  int j;			/* used for debug only */

  node = t->m_nodes + id;
  assert(node->m_id == id && id >= 0);
  if (edge_span(node) >= 0 &&
      node_contains(node, s1_len)) {
    assert(-1 == node->m_child);
  } else if (edge_span(node) >= 0 &&
             node_end_char(t, node) == END_STRING) {
    assert(-1 == node->m_child);
  } else {
    child = node->m_child;

/* fprintf(stderr,"DEBUG entering sum_cs_lens: at node %d, depth %d\n",node->m_id,depth); */
    while (child > 0) {
      nc = t->m_nodes + child;
/* fprintf(stderr,"DEBUG sum_cs_lens: at node %d, depth %d, checking child nc-id=%d\n",node->m_id,depth,nc->m_id); */
      sum_cs_lens(t, s1_len, mincs, child, depth + edge_span(node) + 1,
		      sum_CS_cnt, sum_CS_lens);
      child = nc->m_sibling;
      if (nc->m_in_s1 && t1==-1) {
        t1 = nc->m_begin_char_idx;
/* fprintf(stderr,"DEBUG sum_cs_lens: child in s1 at node %d, depth %d, nc-id=%d, t1=%d\n",node->m_id,depth,nc->m_id,t1); */
      }
      else if (nc->m_in_s2) {
        t2 = nc->m_begin_char_idx;
/* fprintf(stderr,"DEBUG sum_cs_lens: child in s2 at node %d, depth %d, nc-id=%d, t2=%d\n",node->m_id,depth,nc->m_id,t2); */
      }
    }

/* fprintf(stderr,"DEBUG sum_cs_lens: at node %d, depth %d, nc-id=%d, t1=%d, t2=%d, s1_len=%d\n",node->m_id,depth,nc->m_id,t1,t2,s1_len); */

    if (node->m_in_s1 && node->m_in_s2 && t1<=s1_len && t2>s1_len) {
      len = depth + edge_span(node) + 1;
      if (len>=mincs) {
	++(*sum_CS_cnt);
        *sum_CS_lens += len;

/*
#ifdef DEBUG
fprintf(stderr,"DEBUG sum_cs_lens: at node %d, depth %d, adding %d to sum_CS_lens = %d\n",node->m_id,depth,len,*sum_CS_lens);
fprintf(stderr,"DEBUG sum_cs_lens: length=%d, cnt=%d sum_CS_lens=%u\n(",len,*sum_CS_cnt,*sum_CS_lens);
for (j=t1-len;j<t1;j++) { printf("%c", t->m_str[j]); } printf(")\n");
#endif
*/

      }
    }
/* fprintf(stderr,"DEBUG exiting sum_cs_lens: at node %d, depth %d\n",node->m_id,depth); */
  }
  assert(node->m_in_s1 || node->m_in_s2);
}



/*
 * randomly choose one of the common substrings with probability being
 * determined by
 *   sum length of common substrs traversed / sum length all common substrs
 *
 * note that different common substrings will differ at their child nodes
 * rather than at their parent nodes, so don't have to worry about trying
 * to extract different substrings by starting at different ancestor nodes
 */

static void choose_a_cs(Suffix_Tree *t, int s1_len, int mincs, long sum_CS_lens, int id, int depth, long *sum_traversed_CS, int *chosen_LCS_len, int *pos1, int *pos2)
{
  Suffix_Tree_Node *node, *nc;
  int t1=-1, t2=-1;
  int len=0;
  int child;
  long rnd;
  int j;			/* used for debug only */

  node = t->m_nodes + id;
  assert(node->m_id == id && id >= 0);
  if (*chosen_LCS_len>0) { return; } /* already chosen one so exit out */
  if (edge_span(node) >= 0 &&
      node_contains(node, s1_len)) {
    assert(-1 == node->m_child);
  } else if (edge_span(node) >= 0 &&
             node_end_char(t, node) == END_STRING) {
    assert(-1 == node->m_child);
  } else {
    child = node->m_child;
    /*    assert(node->m_child > 0); */

/* fprintf(stderr,"DEBUG choose_a_cs: entering depth=%d\n",depth); */

    while (child > 0) {
      Suffix_Tree_Node *nc = t->m_nodes + child;
      choose_a_cs(t, s1_len, mincs, sum_CS_lens,
		      child, depth + edge_span(node) + 1,
		      sum_traversed_CS, chosen_LCS_len, pos1, pos2);
      child = nc->m_sibling;
      if (nc->m_in_s1) {
        t1 = nc->m_begin_char_idx;
      }
      else if (nc->m_in_s2) {
        t2 = nc->m_begin_char_idx;
      }

/* fprintf(stderr,"DEBUG choose_a_cs: depth=%d, id=%d, nc-id=%d, t1=%d, t2=%d, s1_len=%d, len=%d\n",depth,node->m_id,nc->m_id,t1,t2,s1_len,len); */

    }

/* fprintf(stderr,"DEBUG choose_a_cs: depth=%d\n",depth); */

    /* if this node is common to both strings, and there are child nodes in
     * different strings (given by t1 & t2 which specify the index for the
     * next character in the two strings), then this node is part of a
     * common substring
     */

    if (node->m_in_s1 && node->m_in_s2 && t1<=s1_len && t2>s1_len) {
      len = depth + edge_span(node) + 1;
/*
#ifdef DEBUG
fprintf(stderr,"DEBUG choose_a_cs: cs at 1:%d, 2:%d, length=%d (",t1-len,t2-len-s1_len-1,len);
for (j=t1-len;j<t1;j++) { printf("%c", t->m_str[j]); } printf(")\n");
#endif
*/

      if (len>=mincs) {
        *sum_traversed_CS += len;
        rnd=rand();

/*
#ifdef DEBUG
fprintf(stderr,"DEBUG choose_a_cs: rnd=%dl RAND_MAX=%dl :%f\n",rnd,RAND_MAX,(double)rnd/RAND_MAX);
fprintf(stderr,"DEBUG choose_a_cs: sum_traversed_CS=%d sum_CS_lens=%d thresh=%f\n", *sum_traversed_CS, sum_CS_lens, (double)(*sum_traversed_CS)/sum_CS_lens);
#endif
*/

        if ((double)(*sum_traversed_CS)/sum_CS_lens>(double)rnd/RAND_MAX) {
	  *chosen_LCS_len=len;
	  *pos1=t1-len;
	  *pos2=t2-len-s1_len-1;
/* fprintf(stderr,"DEBUG choose_a_cs: chosen lcs at 1:%d, 2:%d, length=%d\n",t1-len,t2-len-s1_len-1,len); */
        }
      }
    }
  }
  assert(node->m_in_s1 || node->m_in_s2);

/* fprintf(stderr,"DEBUG choose_a_cs: exiting depth=%d\n",depth); */

}



static void calc_lcs(Suffix_Tree *t, int s1_len, int id, int depth,
              int *len, int *pos1, int *pos2)
{
  Suffix_Tree_Node *node, *nc; 
  int child, t1, t2;

  node = t->m_nodes + id;
/* fprintf(stderr,"DEBUG entering calc_lcs... (len=%d)\n",*len); */

  assert(node->m_id == id && id >= 0);
  if (edge_span(node) >= 0 && node_contains(node, s1_len)) {
    assert(-1 == node->m_child);
  } else if (edge_span(node) >= 0 && node_end_char(t, node) == END_STRING) {
    assert(-1 == node->m_child);
  } else {
    child = node->m_child;
    /*    assert(node->m_child > 0); */

    while (child > 0) {
      nc = t->m_nodes + child;
      calc_lcs(t, s1_len, child, depth + edge_span(node) + 1, len, pos1, pos2);
      child = nc->m_sibling;
      if (nc->m_in_s1) {
        t1 = nc->m_begin_char_idx;
      }
      if (nc->m_in_s2) {
        t2 = nc->m_begin_char_idx;
      }
    }

    /* len gives length of lcsstr so far; depth is sum of characters at node;
     * edge span gives number of characters on this edge; t1 & t2 (from 
     * child nodes) give the index for the next character in the strings -
     * the child nodes of a common substring will contain the indexes of the
     * next character in both strings, and in the case of string 1, this may
     * be the index of the separator character (DOLLAR_SIGN), or for string 2
     * the terminator character (END_STRING)
     */

    if (node->m_in_s1 && node->m_in_s2 && *len < depth + edge_span(node) + 1) {
      *len = depth + edge_span(node) + 1;
      *pos1=t1-*len;
      *pos2=t2-*len-s1_len-1;
/* fprintf(stderr,"DEBUG calc_lcs: lcs at 1:%d, 2:%d, length=%d\n",t1-*len,t2-*len-s1_len-1,*len); */
/* fprintf(stderr,"DEBUG calc_lcs: lcs at 1:%d, 2:%d, length=%d\n",*pos1,*pos2,*len); */
    }
  }
  assert(node->m_in_s1 || node->m_in_s2);
/* fprintf(stderr,"DEBUG exiting calc_lcs...\n"); */

}



static void traverse_mark(Suffix_Tree *t, int s1_len, int id)
{
  Suffix_Tree_Node *node = t->m_nodes + id;
  assert(node->m_id == id && id >= 0);
  node->m_in_s1 = 0;
  node->m_in_s2 = 0;
  if (edge_span(node) >= 0 &&
      node_contains(node, s1_len)) {
    assert(-1 == node->m_child);
    node->m_in_s1 = 1;
  } else if (edge_span(node) >= 0 &&
             node_end_char(t, node) == END_STRING) {
    assert(-1 == node->m_child);
    node->m_in_s2 = 1;
  } else {
    int child = node->m_child;
    /*    assert(node->m_child > 0); */
    while (child > 0) {
      Suffix_Tree_Node *nc = t->m_nodes + child;
      traverse_mark(t, s1_len, child);
      child = nc->m_sibling;
      if (nc->m_in_s1) node->m_in_s1 = 1;
      if (nc->m_in_s2) node->m_in_s2 = 1;
    }
  }
  assert(node->m_in_s1 || node->m_in_s2);
}



static void lcs(int fndflag, int mincs, int *sum_CS_cnt, long *sum_CS_lens,
		int *pos1, int *pos2, int *len,
	        int *rpos1, int *rpos2, int *rlen,
                char const *s1, int s1_len,
                char const *s2, int s2_len)
{
  char *buff = (char *)malloc(sizeof(const char) * (s1_len + s2_len + 2));
  Suffix_Tree t;
  long sum_traversed_CS;
  int i;

  strncpy((char*)buff, (char*)s1, s1_len);
  buff[s1_len] = DOLLAR_SIGN;  /* as '$' */
  strncpy(buff + s1_len + 1, s2, s2_len);
  buff[s1_len + s2_len + 1] = 0;

/* fprintf(stderr,"DEBUG lcs:      000000000011111111112222222222333333333344444444445\n"); */
/* fprintf(stderr,"DEBUG lcs:      012345678901234567890123456789012345678901234567890\n"); */
/* fprintf(stderr,"DEBUG lcs: buff=%s\n",buff); */

  suffix_tree_init(&t, buff);

  /* construct child and sibling
     first move node to their proper destination based on their id */

  for (i = 0; i < t.m_hash_base; i++) {
    Suffix_Tree_Node *node = t.m_nodes + i;
    while (node->m_id > 0 && node->m_id != i) {
      Suffix_Tree_Node tmp = t.m_nodes[node->m_id];
      t.m_nodes[node->m_id] = *node;
      *node = tmp;
    }
  }
    
  /* set up root (node 0) */
  ctor_node(t.m_nodes, 0, -1, -1, 0);

  /* construct the tree */
  for (i = 1; i < t.m_hash_base; i++) {
    Suffix_Tree_Node *node = t.m_nodes + i;
    Suffix_Tree_Node *parent;
    if (node->m_id <= 0) {
      break;
    }
    parent = t.m_nodes + node->m_parent;
    node->m_sibling = parent->m_child;
    parent->m_child = node->m_id;
  }

  /* post order traversal */

  traverse_mark(&t, s1_len, 0);

/*
#ifdef DEBUG
  printf(" pnt  id  >=  <=  in1 in2 suff\n");
  print(&t);
#endif
*/


  *len=*rlen=0;
  *pos1=*rpos1=-1;
  *pos2=*rpos2-1;
  *sum_CS_lens=0;
  *sum_CS_cnt=0;

  if (fndflag!=RNDONLY) {		 /* find the LCSSt */
    calc_lcs(&t, s1_len, 0, 0, len, pos1, pos2);
/* fprintf(stderr,"DEBUG lcs: found LCS\n"); */
    if (*len<mincs) {
      *len=0;
      *pos1=*pos2=-1;
      suffix_tree_destroy(&t);
      free(buff);
      return;				/* no point continuing */
    }
  }

  if (*len > 0) {
/* fprintf(stderr,"DEBUG lcs: len, pos1, pos2: %d %d %d\n",*len,*pos1,*pos2); */
    assert(*pos1 >= 0);
    assert(*pos2 >= 0);
  }

  if (fndflag!=MAXONLY) {		/* choose a common str randomly */
    sum_cs_lens(&t, s1_len, mincs, 0, 0, sum_CS_cnt, sum_CS_lens);
    sum_traversed_CS=0;
    choose_a_cs(&t, s1_len, mincs, *sum_CS_lens, 0, 0, &sum_traversed_CS, rlen, rpos1, rpos2);
/* fprintf(stderr,"DEBUG lcs: chose a CS\n"); */
  }

  if (*rlen > 0) {
/* fprintf(stderr,"DEBUG lcs: rlen, rpos1, rpos2: %d %d %d\n",*rlen,*rpos1,*rpos2); */
    assert(*rpos1 >= 0);
    assert(*rpos2 >= 0);
  }

  suffix_tree_destroy(&t);
  free(buff);
}



void usage(char *exename)
{
  unsigned long maxstr=MAX_STRING;
  fprintf(stderr, "\nUsage: %s [-r | -a] [-n mincs]] [-m maxsize | str1 str2]\n\n", exename);
  fprintf(stderr, "Arguments:\n");
  fprintf(stderr, "\t -r\t\treturn one of the common strings chosen randomly.\n");
  fprintf(stderr, "\t -a\t\treturn longest and random common strings, and\n\t\t\tcount and sum of all potential common strings\n");
  fprintf(stderr, "\t -n mincs\tset the smallest common string length that will\n\t\t\tbe considered (the default is 1).\n");
  fprintf(stderr, "\t -m maxsize\tset the maximum string size when reading from\n\t\t\tstdin (the default is %u).\n", maxstr);
  fprintf(stderr, "\t str1 str2\tThe 2 strings to compare. If not supplied as args\n\t\t\tthen each is read as a line from stdin.\n");
  fprintf(stderr, "\nReturns:\n\t The longest and random common strings are returned as line(s)\n\t of tab-delimited integers containing string 1 and 2 start indexes\n\t (starting at 0; -1 for no match) and length of common substring.\n\t The count and sum of potential common strings are also returned\n\t as a line of 2 tab-delimited integers\n");
  fprintf(stderr, "\nDescription:\n\t %s takes 2 strings and returns the indexes and length of the\n\t longest common substring (by default) and/or a randomly chosen\n\t common substring, with longer substrings having a higher\n\t probability of being chosen.\n",exename);
  fprintf(stderr, "\nNote:\n\t The sum and count of potential common substrings may not be quite\n\t what is expected, and can differ depending on the ordering of the\n\t 2 strings! This is due to the use of a suffix tree, whereby the\n\t edges are split on different suffixes, whilst the preceding string\n\t is common. It is actually the sum and count of the different\n\t suffixes (the paths from the root of the suffix tree to different\n\t parent nodes of leaves - where the next character in the strings\n\t diverge) that is returned.\n");
  fprintf(stderr, "\n");

}



int main(int argc, char **argv)
{

   /*
        char str1[5000] = "missixissipix";
    char str2[5000] = "mxissisxsip";
        char str1[5000] = "missixxixssip";
    char str2[5000] = "xmxissisxsip";
    	char str1[5000] = "mipssixissip";
    char str2[5000] = "xmxissisxsip";
        char str1[5000] = "mipssixissip";
    char str2[5000] = "xmxissisxsip";
    */

  char    *str1,
          *str2,
	  *ret,
	  *exename=argv[0];
  int fndflag=MAXONLY,			/* flag for get LCS and/or random CS */
      mincs=1;
  int pos1=-1, pos2=-1, len=0;
  int rpos1=-1, rpos2=-1, rlen=0;
  long sum_CS_lens=0;
  int sum_CS_cnt=0;
  unsigned long max_str_size=MAX_STRING;

/*
  if (argc>1 && *argv[1]=='-' && argv[1][1]=='d') {
    #define DEBUG;
    --argc;
    ++argv;
  }
*/

  while (argc>1 && *argv[1]=='-') {
    /* use MAXONLY to check that both -a and -r args are not given */
    if (strcmp(argv[1],"-a")==0 && fndflag==MAXONLY) {
      fndflag=MAXNRND;
      ++argv;
      --argc;
      continue;
    }
    if (strcmp(argv[1],"-r")==0 && fndflag==MAXONLY) {
      fndflag=RNDONLY;
      ++argv;
      --argc;
      continue;
    }
    else if ((strcmp(argv[1],"-n")==0) && argc>2 &&
		    sscanf(argv[2],"%u",&mincs)) {
      argv+=2;
      argc-=2;
      }
    else if ((strcmp(argv[1],"-m")==0) && argc>2 &&
		    sscanf(argv[2],"%u",&max_str_size)) {
      argv+=2;
      argc-=2;
      continue;
    }
    else {
      usage(exename);
      exit(1);
    }
  }



  /* at this point the only args should be strings */

  if (argc<2) {
    assert(str1=(char*)malloc(max_str_size+1));
    assert(str2=(char*)malloc(max_str_size+1));
    fprintf(stderr,"enter string 1: ");
    scanf("%s",str1);
    if (strlen(str1)>max_str_size) {
      fprintf(stderr,"\nstring 1 exceeded storage limit of %u chars specified by the -m arg!\nexiting...\n",max_str_size);
      exit(1);
    }
    fprintf(stderr,"enter string 2: ");
    scanf("%s",str2);
    if (strlen(str2)>max_str_size) {
      fprintf(stderr,"\nstring 2 exceeded storage limit of %u chars specified by the -m arg!\nexiting...\n",max_str_size);
      exit(1);
    }
  } else if (argc==3) {
    str1=argv[1];
    str2=argv[2];
  } else {
    usage(exename);
    exit(1);
  }


/*
fprintf(stderr,"DEBUG main:    000000000011111111112222222222333333333344444444445\n");
fprintf(stderr,"DEBUG main:    012345678901234567890123456789012345678901234567890\n"); 
fprintf(stderr,"DEBUG main: 1: %s\n",str1);
fprintf(stderr,"DEBUG main: 2: %s\n",str2);
*/


  /* srand(uclock()); */
/*  srand((unsigned int)time(NULL)); */ /* seed with no. of seconds since 1/1/70 */

  srand(getpid());
  srand(rand());

/*
fprintf(stderr,"DEBUG main: calling lcs() with string lengths %d, %d ...\n",strlen(str1),strlen(str2));
*/

  lcs(fndflag, mincs, &sum_CS_cnt, &sum_CS_lens,
		  &pos1, &pos2, &len, &rpos1, &rpos2, &rlen,
	  	  str1, strlen(str1), str2, strlen(str2));

  switch (fndflag) {
    case MAXONLY:
      printf("%d\t%d\t%d\n",pos1,pos2,len);
      break;
    case RNDONLY:
      printf("%d\t%d\t%d\n",rpos1,rpos2,rlen);
      break;
    default:
      printf("%d\t%d\t%d\n",pos1,pos2,len);
      printf("%d\t%d\t%d\n",rpos1,rpos2,rlen);
      printf("%d\t%d\n",sum_CS_cnt,sum_CS_lens);
  }


  if (argc<2) {
    free(str1);
    free(str1);

  }

/*
fprintf(stderr,"DEBUG main: cleaned up... exiting main()\n");
*/

  return 0;
}



